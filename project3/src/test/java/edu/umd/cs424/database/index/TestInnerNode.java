package edu.umd.cs424.database.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import edu.umd.cs424.database.common.Buffer;
import edu.umd.cs424.database.common.Pair;
import edu.umd.cs424.database.databox.DataBox;
import edu.umd.cs424.database.databox.IntDataBox;
import edu.umd.cs424.database.databox.Type;
import edu.umd.cs424.database.io.PageAllocator;
import edu.umd.cs424.database.table.RecordId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Timeout(1)
public class TestInnerNode {
    public static final String testFile = "TestInnerNode";

    // The page allocator associated with the TestInnerNode file. See
    // resetMembers for initialization.
    PageAllocator allocator;

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempFolder;

    // inner, leaf0, leaf1, and leaf2 collectively form the following B+ tree:
    //
    //                               inner
    //                               +----+----+----+----+
    //                               | 10 | 20 |    |    |
    //                               +----+----+----+----+
    //                              /     |     \
    //                         ____/      |      \____
    //                        /           |           \
    //   +----+----+----+----+  +----+----+----+----+  +----+----+----+----+
    //   |  1 |  2 |  3 |    |  | 11 | 12 | 13 |    |  | 21 | 22 | 23 |    |
    //   +----+----+----+----+  +----+----+----+----+  +----+----+----+----+
    //   leaf0                  leaf1                  leaf2
    //
    // innerKeys, innerChildren, keys0, rids0, keys1, rids1, keys2, and rids2
    // hold *copies* of the contents of the nodes. To test out a certain method
    // of a tree---for example, put---we can issue a put against the tree,
    // update one of innerKeys, innerChildren, keys{0,1,2}, or rids{0,1,2}, and
    // then check that the contents of the tree match our expectations. For
    // example:
    //
    //   var key = new IntDataBox(4);
    //   var rid = new RecordId(4, (short) 4);
    //   inner.put(key, rid);
    //
    //   // (4, (4, 4)) is added to leaf 0, so we update keys0 and rids0 and
    //   // check that it matches the contents of leaf0.
    //   keys0.add(key);
    //   rids0.add(rid);
    //   assertEquals(keys0, getLeaf(leaf0).getKeys());
    //   assertEquals(rids0, getLeaf(leaf0).getRids());
    //
    //   // Leaf 1 should be unchanged which we can check:
    //   assertEquals(keys1, getLeaf(leaf1).getKeys());
    //   assertEquals(rids1, getLeaf(leaf1).getRids());
    //
    //   // Writing all these assertEquals is boilerplate, so we can abstract
    //   // it in checkTreeMatchesExpectations().
    //   checkTreeMatchesExpectations();
    //
    // Note that we cannot simply store the LeafNodes as members because when
    // we call something like inner.put(k), the inner node constructs a new
    // LeafNode from the serialization and forwards the put to that. It would
    // not affect our the in-memory values of our members. Also note that all
    // of these members are initialized by resetMembers before every test case
    // is run.
    private List<DataBox> innerKeys;
    private List<Integer> innerChildren;
    private InnerNode inner;
    private List<DataBox> keys0;
    private List<RecordId> rids0;
    private int leaf0;
    private List<DataBox> keys1;
    private List<RecordId> rids1;
    private int leaf1;
    private List<DataBox> keys2;
    private List<RecordId> rids2;
    private int leaf2;

    // See comment above.
    @BeforeEach
    public void resetMembers() throws IOException {
        Path file = Files.createFile(tempFolder.resolve(testFile));
        this.allocator = new PageAllocator(file.toAbsolutePath().toString(), false, null);

        BPlusTreeMetadata meta = getBPlusTreeMetadata(Type.intType(), 5);

        // Leaf 2
        var keys2 = new ArrayList<DataBox>();
        keys2.add(new IntDataBox(21));
        keys2.add(new IntDataBox(22));
        keys2.add(new IntDataBox(23));
        var rids2 = new ArrayList<RecordId>();
        rids2.add(new RecordId(21, (short) 21));
        rids2.add(new RecordId(22, (short) 22));
        rids2.add(new RecordId(23, (short) 23));
        Optional<Integer> sibling2 = Optional.empty();
        var leaf2 = new LeafNode(meta, keys2, rids2, sibling2, null);

        this.keys2 = new ArrayList<>(keys2);
        this.rids2 = new ArrayList<>(rids2);
        this.leaf2 = leaf2.getPage().getPageNum();

        // Leaf 1
        keys1 = new ArrayList<>();
        keys1.add(new IntDataBox(11));
        keys1.add(new IntDataBox(12));
        keys1.add(new IntDataBox(13));
        rids1 = new ArrayList<>();
        rids1.add(new RecordId(11, (short) 11));
        rids1.add(new RecordId(12, (short) 12));
        rids1.add(new RecordId(13, (short) 13));
        Optional<Integer> sibling1 = Optional.of(leaf2.getPage().getPageNum());
        var leaf1 = new LeafNode(meta, keys1, rids1, sibling1, null);

        this.keys1 = new ArrayList<>(keys1);
        this.rids1 = new ArrayList<>(rids1);
        this.leaf1 = leaf1.getPage().getPageNum();

        // Leaf 0
        var keys0 = new ArrayList<DataBox>();
        keys0.add(new IntDataBox(1));
        keys0.add(new IntDataBox(2));
        keys0.add(new IntDataBox(3));
        var rids0 = new ArrayList<RecordId>();
        rids0.add(new RecordId(1, (short) 1));
        rids0.add(new RecordId(2, (short) 2));
        rids0.add(new RecordId(3, (short) 3));
        Optional<Integer> sibling0 = Optional.of(leaf1.getPage().getPageNum());
        var leaf0 = new LeafNode(meta, keys0, rids0, sibling0, null);
        this.keys0 = new ArrayList<>(keys0);
        this.rids0 = new ArrayList<>(rids0);
        this.leaf0 = leaf0.getPage().getPageNum();

        // Inner node
        var innerKeys = new ArrayList<DataBox>();
        innerKeys.add(new IntDataBox(10));
        innerKeys.add(new IntDataBox(20));

        var innerChildren = new ArrayList<Integer>();
        innerChildren.add(this.leaf0);
        innerChildren.add(this.leaf1);
        innerChildren.add(this.leaf2);

        this.innerKeys = new ArrayList<>(innerKeys);
        this.innerChildren = new ArrayList<>(innerChildren);
        this.inner = new InnerNode(meta, innerKeys, innerChildren, null);
    }

    // See comment above.
    private LeafNode getLeaf(int pageNum) throws IOException {
        BPlusTreeMetadata meta = getBPlusTreeMetadata(Type.intType(), 5);
        return LeafNode.fromBytes(null, meta, pageNum);
    }

    // See comment above.
    private void checkTreeMatchesExpectations() throws IOException {
        LeafNode leaf0 = getLeaf(this.leaf0);
        LeafNode leaf1 = getLeaf(this.leaf1);
        LeafNode leaf2 = getLeaf(this.leaf2);

        assertEquals(keys0, leaf0.getKeys());
        assertEquals(rids0, leaf0.getRids());
        assertEquals(keys1, leaf1.getKeys());
        assertEquals(rids1, leaf1.getRids());
        assertEquals(keys2, leaf2.getKeys());
        assertEquals(rids2, leaf2.getRids());
        assertEquals(innerKeys, inner.getKeys());
        assertEquals(innerChildren, inner.getChildren());
    }

    private BPlusTreeMetadata getBPlusTreeMetadata(Type keySchema, int order)
    throws IOException {
        return new BPlusTreeMetadata(allocator, keySchema, order);
    }

    // Tests ///////////////////////////////////////////////////////////////////
    @Test
    public void testGet() throws IOException {
        LeafNode leaf0 = getLeaf(this.leaf0);
        for (int i = 0; i < 10; ++i) {
            assertEquals(leaf0, inner.get(null, new IntDataBox(i)));
        }

        LeafNode leaf1 = getLeaf(this.leaf1);
        for (int i = 10; i < 20; ++i) {
            assertEquals(leaf1, inner.get(null, new IntDataBox(i)));
        }

        LeafNode leaf2 = getLeaf(this.leaf2);
        for (int i = 20; i < 30; ++i) {
            assertEquals(leaf2, inner.get(null, new IntDataBox(i)));
        }
    }

    @Test
    public void testGetLeftmostLeaf() throws IOException {
        assertEquals(getLeaf(leaf0), inner.getLeftmostLeaf(null));
    }

    @Test
    public void testPut_noOverflow() throws BPlusTreeException, IOException {
        IntDataBox key = null;
        RecordId rid = null;

        // Add to leaf 0.
        key = new IntDataBox(0);
        rid = new RecordId(0, (short) 0);
        assertEquals(Optional.empty(), inner.put(null, key, rid));
        keys0.add(0, key);
        rids0.add(0, rid);
        checkTreeMatchesExpectations();

        // Add to leaf 1.
        key = new IntDataBox(14);
        rid = new RecordId(14, (short) 14);
        assertEquals(Optional.empty(), inner.put(null, key, rid));
        keys1.add(3, key);
        rids1.add(3, rid);
        checkTreeMatchesExpectations();

        // Add to leaf 2.
        key = new IntDataBox(20);
        rid = new RecordId(20, (short) 20);
        assertEquals(Optional.empty(), inner.put(null, key, rid));
        keys2.add(0, key);
        rids2.add(0, rid);
        checkTreeMatchesExpectations();
    }

    // Creates a leaf holding `keys` (each with record id (k, k)) whose right
    // sibling is `rightSibling`, and returns its page number.
    private int makeLeaf(BPlusTreeMetadata meta, Optional<Integer> rightSibling, int... keys) {
        var leafKeys = new ArrayList<DataBox>();
        var leafRids = new ArrayList<RecordId>();
        for (int k : keys) {
            leafKeys.add(new IntDataBox(k));
            leafRids.add(new RecordId(k, (short) k));
        }
        return new LeafNode(meta, leafKeys, leafRids, rightSibling, null).getPage().getPageNum();
    }

    private static String leafSexp(int... keys) {
        var ss = new ArrayList<String>();
        for (int k : keys) {
            ss.add(String.format("(%d (%d %d))", k, k, k));
        }
        return String.format("(%s)", String.join(" ", ss));
    }

    @Test
    public void testPut_lectureSlidesExample() throws BPlusTreeException, IOException {
        // The n = 4 example from the lecture slides ("Another Example: INSERT
        // (125)"). A node holds at most 3 keys. The slides do not show the
        // subtrees under 1000, 2000, and 3000, so single leaves stand in for
        // them here.
        //
        //                                  (1000 2000 3000)
        //                                 /     |     |    \
        //                  (100 200 300)     (1000) (2000) (3000)
        //                 /    |    |    \
        //   (10 20 30) (100 130 150) (200 230 240) (300 330 350)
        //              L
        BPlusTreeMetadata meta = getBPlusTreeMetadata(Type.intType(), 4);
        int s3000 = makeLeaf(meta, Optional.empty(), 3000);
        int s2000 = makeLeaf(meta, Optional.of(s3000), 2000);
        int s1000 = makeLeaf(meta, Optional.of(s2000), 1000);
        int l3 = makeLeaf(meta, Optional.of(s1000), 300, 330, 350);
        int l2 = makeLeaf(meta, Optional.of(l3), 200, 230, 240);
        int l1 = makeLeaf(meta, Optional.of(l2), 100, 130, 150);
        int l0 = makeLeaf(meta, Optional.of(l1), 10, 20, 30);
        var p = new InnerNode(meta,
                new ArrayList<>(Arrays.asList(new IntDataBox(100), new IntDataBox(200), new IntDataBox(300))),
                new ArrayList<>(Arrays.asList(l0, l1, l2, l3)), null);
        var root = new InnerNode(meta,
                new ArrayList<>(Arrays.asList(new IntDataBox(1000), new IntDataBox(2000), new IntDataBox(3000))),
                new ArrayList<>(Arrays.asList(p.getPage().getPageNum(), s1000, s2000, s3000)), null);

        // Step 1: L overflows to (100 125 130 150) and keeps ceil(4/2) = 2 keys,
        // so L = (100 125) and L' = (130 150); 130 goes up into P.
        // Steps 2-3: P overflows to (100 130 200 300) and keeps ceil(5/2) = 3
        // pointers, so P = (100 130) and P' = (300); 200 goes up into the root.
        // Steps 4-5: the root overflows to (200 1000 2000 3000) and keeps 3
        // pointers, so it becomes (200 1000) with a new right sibling (3000);
        // 2000 goes up and becomes the key of the new root.
        Optional<Pair<DataBox, Integer>> split = root.put(null, new IntDataBox(125), new RecordId(125, (short) 125));
        assertEquals(Optional.of(new IntDataBox(2000)), split.map(Pair::getFirst));

        String pSexp = String.format("(%s 100 %s 130 %s)",
                leafSexp(10, 20, 30), leafSexp(100, 125), leafSexp(130, 150));
        String pPrimeSexp = String.format("(%s 300 %s)", leafSexp(200, 230, 240), leafSexp(300, 330, 350));
        String left = String.format("(%s 200 %s 1000 %s)", pSexp, pPrimeSexp, leafSexp(1000));
        String right = String.format("(%s 3000 %s)", leafSexp(2000), leafSexp(3000));
        assertEquals(left, InnerNode.fromBytes(null, meta, root.getPage().getPageNum()).toSexp(null));
        assertEquals(right, InnerNode.fromBytes(null, meta, split.get().getSecond()).toSexp(null));

        // The leaves are still linked in key order.
        var leaf = LeafNode.fromBytes(null, meta, l0);
        var scanned = new ArrayList<DataBox>();
        while (true) {
            leaf.scanKeys().forEachRemaining(scanned::add);
            Optional<LeafNode> next = leaf.getRightSibling(null);
            if (next.isEmpty()) {
                break;
            }
            leaf = next.get();
        }
        var expected = new ArrayList<DataBox>();
        for (int k : new int[] {10, 20, 30, 100, 125, 130, 150, 200, 230, 240, 300, 330, 350, 1000, 2000, 3000}) {
            expected.add(new IntDataBox(k));
        }
        assertEquals(expected, scanned);
    }

    @Test
    public void testRemove() throws BPlusTreeException, IOException {
        // A leaf needs at least ceil(4/2) = 2 keys, and a non-root inner node
        // needs at least ceil(5/2) = 3 pointers. The first steps follow the
        // example in BPlusNode.remove.
        BPlusTreeMetadata meta = getBPlusTreeMetadata(Type.intType(), 5);
        assertEquals(Optional.empty(), inner.put(null, new IntDataBox(14), new RecordId(14, (short) 14)));
        assertFalse(inner.remove(null, new IntDataBox(3)));
        assertEquals(String.format("(%s 10 %s 20 %s)",
                leafSexp(1, 2), leafSexp(11, 12, 13, 14), leafSexp(21, 22, 23)), inner.toSexp(null));

        // leaf0 is left with (2), which is too few. It is the first child, so
        // it uses the next child leaf1. 1 + 4 keys do not fit in one leaf, so
        // leaf0 borrows the first key of leaf1, and 10 is replaced by the new
        // first key of leaf1.
        assertFalse(inner.remove(null, new IntDataBox(1)));
        assertEquals(String.format("(%s 12 %s 20 %s)",
                leafSexp(2, 11), leafSexp(12, 13, 14), leafSexp(21, 22, 23)), inner.toSexp(null));

        // leaf2 is left with (23), so it uses the previous child leaf1. 4 + 1
        // keys do not fit in one leaf, so leaf2 borrows the last key of leaf1,
        // which replaces 20.
        assertEquals(Optional.empty(), inner.put(null, new IntDataBox(15), new RecordId(15, (short) 15)));
        assertFalse(inner.remove(null, new IntDataBox(21)));
        assertFalse(inner.remove(null, new IntDataBox(22)));
        assertEquals(String.format("(%s 12 %s 15 %s)",
                leafSexp(2, 11), leafSexp(12, 13, 14), leafSexp(15, 23)), inner.toSexp(null));

        // Removing 12 leaves 2 keys in leaf1, which is enough. The key 12 in
        // inner stays.
        assertFalse(inner.remove(null, new IntDataBox(12)));
        assertEquals(String.format("(%s 12 %s 15 %s)",
                leafSexp(2, 11), leafSexp(13, 14), leafSexp(15, 23)), inner.toSexp(null));

        // leaf2 is left with (15). 2 + 1 keys fit in one leaf, so leaf2 is
        // merged into leaf1, and inner removes 15 and the pointer to leaf2.
        // inner is left with 2 pointers, which is too few.
        assertTrue(inner.remove(null, new IntDataBox(23)));
        assertEquals(String.format("(%s 12 %s)", leafSexp(2, 11), leafSexp(13, 14, 15)), inner.toSexp(null));
        assertEquals(Arrays.asList(leaf0, leaf1), inner.getChildren());
        assertEquals(Arrays.asList(2, 11, 13, 14, 15), leafChainKeys(meta, leaf0));

        // Removing a key that is not in the tree does nothing.
        assertFalse(inner.remove(null, new IntDataBox(23)));
        assertEquals(String.format("(%s 12 %s)", leafSexp(2, 11), leafSexp(13, 14, 15)), inner.toSexp(null));
    }

    // Returns the keys of the leaves reached by following right sibling
    // pointers from the leaf on page `pageNum`.
    private List<Integer> leafChainKeys(BPlusTreeMetadata meta, int pageNum) {
        var keys = new ArrayList<Integer>();
        Optional<LeafNode> leaf = Optional.of(LeafNode.fromBytes(null, meta, pageNum));
        while (leaf.isPresent()) {
            leaf.get().scanKeys().forEachRemaining(k -> keys.add(k.getInt()));
            leaf = leaf.get().getRightSibling(null);
        }
        return keys;
    }

    // Builds the n = 3 tree from the lecture slides' deletion examples, with
    // Brighton, Clearview, Downtown, Mianus, Perryridge, Redwood, and Round Hill
    // written as 10, 20, ..., 70. Returns the root and the first leaf's page
    // number.
    //
    //                      (50)
    //                    /      \
    //             (30 40)        (60)
    //            /   |   \      /    \
    //     (10 20) (30) (40) (50) (60 70)
    private Pair<InnerNode, Integer> makeSlidesDeletionTree(BPlusTreeMetadata meta) {
        int e = makeLeaf(meta, Optional.empty(), 60, 70);
        int d = makeLeaf(meta, Optional.of(e), 50);
        int c = makeLeaf(meta, Optional.of(d), 40);
        int b = makeLeaf(meta, Optional.of(c), 30);
        int a = makeLeaf(meta, Optional.of(b), 10, 20);
        var left = new InnerNode(meta,
                new ArrayList<>(Arrays.asList(new IntDataBox(30), new IntDataBox(40))),
                new ArrayList<>(Arrays.asList(a, b, c)), null);
        var right = new InnerNode(meta,
                new ArrayList<>(Arrays.asList(new IntDataBox(60))),
                new ArrayList<>(Arrays.asList(d, e)), null);
        var root = new InnerNode(meta,
                new ArrayList<>(Arrays.asList(new IntDataBox(50))),
                new ArrayList<>(Arrays.asList(left.getPage().getPageNum(), right.getPage().getPageNum())), null);
        return new Pair<>(root, a);
    }

    @Test
    public void testRemove_lectureSlidesExample() throws IOException {
        // The n = 3 deletion examples from the lecture slides. A leaf needs at
        // least ceil(2/2) = 1 key, and a non-root inner node needs at least
        // ceil(3/2) = 2 pointers.
        BPlusTreeMetadata meta = getBPlusTreeMetadata(Type.intType(), 3);

        // Deleting "Downtown" (30) empties its leaf, which merges into the
        // previous leaf (10 20). The parent removes 30 and keeps 2 pointers.
        Pair<InnerNode, Integer> tree = makeSlidesDeletionTree(meta);
        InnerNode root = tree.getFirst();
        assertFalse(root.remove(null, new IntDataBox(30)));
        assertEquals(String.format("((%s 40 %s) 50 (%s 60 %s))",
                leafSexp(10, 20), leafSexp(40), leafSexp(50), leafSexp(60, 70)), root.toSexp(null));
        assertEquals(Arrays.asList(10, 20, 40, 50, 60, 70), leafChainKeys(meta, tree.getSecond()));

        // Then deleting "Perryridge" (50) empties its leaf. It is the first
        // child, so (60 70) merges into it, and (60) is left with 1 pointer.
        // The previous inner node (40) has 2 pointers, and 2 + 1 pointers fit
        // in one node, so the two merge with 50 pulled down between them. The
        // root is left with 1 pointer, so BPlusTree.remove would make the
        // merged node the new root.
        assertTrue(root.remove(null, new IntDataBox(50)));
        assertEquals(String.format("((%s 40 %s 50 %s))",
                leafSexp(10, 20), leafSexp(40), leafSexp(60, 70)), root.toSexp(null));
        assertEquals(Arrays.asList(10, 20, 40, 60, 70), leafChainKeys(meta, tree.getSecond()));

        // Deleting "Perryridge" (50) from the original tree instead: (60) is
        // again left with 1 pointer, but the previous inner node (30 40) has 3
        // pointers, and 3 + 1 pointers do not fit in one node. So (60) borrows
        // the last pointer of (30 40), 50 moves down into (60), and 40 moves up
        // into the root.
        tree = makeSlidesDeletionTree(meta);
        root = tree.getFirst();
        assertFalse(root.remove(null, new IntDataBox(50)));
        assertEquals(String.format("((%s 30 %s) 40 (%s 50 %s))",
                leafSexp(10, 20), leafSexp(30), leafSexp(40), leafSexp(60, 70)), root.toSexp(null));
        assertEquals(Arrays.asList(10, 20, 30, 40, 60, 70), leafChainKeys(meta, tree.getSecond()));
    }

    @Test
    public void testMaxOrder() throws InterruptedException {
        // Note that this white box test depend critically on the implementation
        // of toBytes and includes a lot of magic numbers that won't make sense
        // unless you read toBytes.
        assertEquals(4, Type.intType().getSizeInBytes());
        assertEquals(6, RecordId.getSizeInBytes());
        for (int n = 1; n < 21; ++n) {
            for (int i = 5 + ((n - 1) * 4) + (n * 4); i < 5 + (n * 4) + ((n + 1) * 4); ++i) {
                assertEquals(n, InnerNode.maxOrder(i, Type.intType()));
            }
        }
    }

    @Test
    public void testnumLessThanEqual() {
        List<Integer> empty = Arrays.asList();
        assertEquals(0, InnerNode.numLessThanEqual(0, empty));

        List<Integer> contiguous = Arrays.asList(1, 2, 3, 4, 5);
        assertEquals(0, InnerNode.numLessThanEqual(0, contiguous));
        assertEquals(1, InnerNode.numLessThanEqual(1, contiguous));
        assertEquals(2, InnerNode.numLessThanEqual(2, contiguous));
        assertEquals(3, InnerNode.numLessThanEqual(3, contiguous));
        assertEquals(4, InnerNode.numLessThanEqual(4, contiguous));
        assertEquals(5, InnerNode.numLessThanEqual(5, contiguous));
        assertEquals(5, InnerNode.numLessThanEqual(6, contiguous));
        assertEquals(5, InnerNode.numLessThanEqual(7, contiguous));

        List<Integer> sparseWithDuplicates = Arrays.asList(1, 3, 3, 3, 5);
        assertEquals(0, InnerNode.numLessThanEqual(0, sparseWithDuplicates));
        assertEquals(1, InnerNode.numLessThanEqual(1, sparseWithDuplicates));
        assertEquals(1, InnerNode.numLessThanEqual(2, sparseWithDuplicates));
        assertEquals(4, InnerNode.numLessThanEqual(3, sparseWithDuplicates));
        assertEquals(4, InnerNode.numLessThanEqual(4, sparseWithDuplicates));
        assertEquals(5, InnerNode.numLessThanEqual(5, sparseWithDuplicates));
        assertEquals(5, InnerNode.numLessThanEqual(6, sparseWithDuplicates));
        assertEquals(5, InnerNode.numLessThanEqual(7, sparseWithDuplicates));
    }

    @Test
    public void testnumLessThan() {
        List<Integer> empty = Arrays.asList();
        assertEquals(0, InnerNode.numLessThanEqual(0, empty));

        List<Integer> contiguous = Arrays.asList(1, 2, 3, 4, 5);
        assertEquals(0, InnerNode.numLessThan(0, contiguous));
        assertEquals(0, InnerNode.numLessThan(1, contiguous));
        assertEquals(1, InnerNode.numLessThan(2, contiguous));
        assertEquals(2, InnerNode.numLessThan(3, contiguous));
        assertEquals(3, InnerNode.numLessThan(4, contiguous));
        assertEquals(4, InnerNode.numLessThan(5, contiguous));
        assertEquals(5, InnerNode.numLessThan(6, contiguous));
        assertEquals(5, InnerNode.numLessThan(7, contiguous));

        List<Integer> sparseWithDuplicates = Arrays.asList(1, 3, 3, 3, 5);
        assertEquals(0, InnerNode.numLessThan(0, sparseWithDuplicates));
        assertEquals(0, InnerNode.numLessThan(1, sparseWithDuplicates));
        assertEquals(1, InnerNode.numLessThan(2, sparseWithDuplicates));
        assertEquals(1, InnerNode.numLessThan(3, sparseWithDuplicates));
        assertEquals(4, InnerNode.numLessThan(4, sparseWithDuplicates));
        assertEquals(4, InnerNode.numLessThan(5, sparseWithDuplicates));
        assertEquals(5, InnerNode.numLessThan(6, sparseWithDuplicates));
        assertEquals(5, InnerNode.numLessThan(7, sparseWithDuplicates));
    }

    @Test
    public void testToSexp() {
        String leaf0 = "((1 (1 1)) (2 (2 2)) (3 (3 3)))";
        String leaf1 = "((11 (11 11)) (12 (12 12)) (13 (13 13)))";
        String leaf2 = "((21 (21 21)) (22 (22 22)) (23 (23 23)))";
        String expected = String.format("(%s 10 %s 20 %s)", leaf0, leaf1, leaf2);
        assertEquals(expected, inner.toSexp(null));
    }

    @Test
    public void testToAndFromBytes() throws IOException {
        int n = 11;
        BPlusTreeMetadata meta = getBPlusTreeMetadata(Type.intType(), n);

        var keys = new ArrayList<DataBox>();
        var children = new ArrayList<Integer>();
        children.add(42);

        for (int i = 0; i < n - 1; ++i) {
            keys.add(new IntDataBox(i));
            children.add(i);

            var inner = new InnerNode(meta, keys, children, null);
            int pageNum = inner.getPage().getPageNum();
            InnerNode parsed = InnerNode.fromBytes(null, meta, pageNum);
            assertEquals(inner, parsed);
        }
    }

    @Test
    public void testByteSequence() throws IOException {
        int n = 11;
        BPlusTreeMetadata meta = getBPlusTreeMetadata(Type.intType(), n);

        // Create keys
        var keys = new ArrayList<DataBox>();
        keys.add(new IntDataBox(5));
        keys.add(new IntDataBox(8));

        var children = new ArrayList<Integer>();
        children.add(3);
        children.add(1);
        children.add(7);

        var inner = new InnerNode(meta, keys, children, null);

        // Check the byte representation in order.
        Buffer buf = inner.getPage().getBuffer(null);
        assertEquals((byte) 0, buf.get()); // isInner
        assertEquals(keys.size(), buf.getInt()); // number of keys

        assertEquals(children.get(0), buf.getInt());

        for (int i = 0; i < keys.size(); ++i) {
            DataBox key = DataBox.fromBytes(buf, Type.intType());
            assertEquals(keys.get(i), key);
            Integer child = buf.getInt();
            assertEquals(children.get(i + 1), child);
        }
    }
}
