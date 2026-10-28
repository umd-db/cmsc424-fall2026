package edu.umd.cs424.database.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import edu.umd.cs424.database.concurrency.DummyLockContext;
import edu.umd.cs424.database.common.Pair;
import edu.umd.cs424.database.databox.DataBox;
import edu.umd.cs424.database.databox.IntDataBox;
import edu.umd.cs424.database.databox.Type;
import edu.umd.cs424.database.table.RecordId;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(1)
public class TestBPlusTree {
    public static final String filename = "TestBPlusTree";
    private Path file;

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempFolder;

    // Helpers /////////////////////////////////////////////////////////////////
    @BeforeEach
    public void initFile() throws IOException {
        this.file = Files.createFile(tempFolder.resolve(filename));
    }

    private BPlusTree getBPlusTree(Type keySchema, int order) {
        try {
            return new BPlusTree(file.toAbsolutePath().toString(), keySchema, order, new DummyLockContext(), null);
        } catch (BPlusTreeException e) {
            // Why do checked exceptions exist? Why are they even used here?
            throw new RuntimeException(e);
        }
    }

    private static <T> ArrayList<T> iteratorToList(Iterator<T> iter) {
        var xs = new ArrayList<T>();
        while (iter.hasNext()) {
            xs.add(iter.next());
        }
        return xs;
    }

    // Tests ///////////////////////////////////////////////////////////////////

    @Test
    public void testBulkLoad_simple() throws BPlusTreeException {
        BPlusTree tree = getBPlusTree(Type.intType(), 5);
        float fillFactor = 0.75f;
        assertEquals("()", tree.toSexp(null));

        var data = new ArrayList<Pair<DataBox, RecordId>>();
        for (int i = 1; i <= 11; ++i) {
            data.add(new Pair<>(new IntDataBox(i), new RecordId(i, (short) i)));
        }

        tree.bulkLoad(null, data.iterator(), fillFactor);
        //      (    4        7         10        _   )
        //       /       |         |         \
        // (1 2 3 _) (4 5 6 _) (7 8 9 _) (10 11 _ _)
        String leaf0 = "((1 (1 1)) (2 (2 2)) (3 (3 3)))";
        String leaf1 = "((4 (4 4)) (5 (5 5)) (6 (6 6)))";
        String leaf2 = "((7 (7 7)) (8 (8 8)) (9 (9 9)))";
        String leaf3 = "((10 (10 10)) (11 (11 11)))";
        String sexp = String.format("(%s 4 %s 7 %s 10 %s)", leaf0, leaf1, leaf2, leaf3);
        assertEquals(sexp, tree.toSexp(null));
    }

    @Test
    public void testWhiteBoxTest() throws BPlusTreeException, IOException {
        // With n = 3, a node holds at most 2 keys. An overflowing leaf keeps
        // ceil(3/2) = 2 keys and moves 1 right; an overflowing inner node keeps
        // ceil(4/2) = 2 pointers, moves 1 key up, and moves the rest right.
        BPlusTree tree = getBPlusTree(Type.intType(), 3);
        assertEquals("()", tree.toSexp(null));

        // (4)
        tree.put(null, new IntDataBox(4), new RecordId(4, (short) 4));
        assertEquals("((4 (4 4)))", tree.toSexp(null));

        // (4 9)
        tree.put(null, new IntDataBox(9), new RecordId(9, (short) 9));
        assertEquals("((4 (4 4)) (9 (9 9)))", tree.toSexp(null));

        //    (9)
        //   /   \
        // (4 6) (9)
        tree.put(null, new IntDataBox(6), new RecordId(6, (short) 6));
        String l = "((4 (4 4)) (6 (6 6)))";
        String r = "((9 (9 9)))";
        assertEquals(String.format("(%s 9 %s)", l, r), tree.toSexp(null));

        //      (6 9)
        //     /  |  \
        // (2 4) (6) (9)
        tree.put(null, new IntDataBox(2), new RecordId(2, (short) 2));
        l = "((2 (2 2)) (4 (4 4)))";
        String m = "((6 (6 6)))";
        r = "((9 (9 9)))";
        assertEquals(String.format("(%s 6 %s 9 %s)", l, m, r), tree.toSexp(null));

        //       (6 9)
        //      /  |  \
        // (2 4) (6 7) (9)
        tree.put(null, new IntDataBox(7), new RecordId(7, (short) 7));
        l = "((2 (2 2)) (4 (4 4)))";
        m = "((6 (6 6)) (7 (7 7)))";
        r = "((9 (9 9)))";
        assertEquals(String.format("(%s 6 %s 9 %s)", l, m, r), tree.toSexp(null));

        //            (8)
        //           /   \
        //     (6)          (9)
        //    /   \        /   \
        // (2 4) (6 7)   (8)   (9)
        tree.put(null, new IntDataBox(8), new RecordId(8, (short) 8));
        String ll = "((2 (2 2)) (4 (4 4)))";
        String lr = "((6 (6 6)) (7 (7 7)))";
        String rl = "((8 (8 8)))";
        String rr = "((9 (9 9)))";
        l = String.format("(%s 6 %s)", ll, lr);
        r = String.format("(%s 9 %s)", rl, rr);
        assertEquals(String.format("(%s 8 %s)", l, r), tree.toSexp(null));

        //               (8)
        //              /   \
        //      (4 6)          (9)
        //    /   |   \       /   \
        // (2 3) (4) (6 7)  (8)   (9)
        tree.put(null, new IntDataBox(3), new RecordId(3, (short) 3));
        ll = "((2 (2 2)) (3 (3 3)))";
        String lm = "((4 (4 4)))";
        lr = "((6 (6 6)) (7 (7 7)))";
        rl = "((8 (8 8)))";
        rr = "((9 (9 9)))";
        l = String.format("(%s 4 %s 6 %s)", ll, lm, lr);
        r = String.format("(%s 9 %s)", rl, rr);
        assertEquals(String.format("(%s 8 %s)", l, r), tree.toSexp(null));

        //                (8)
        //               /   \
        //      (4 6)            (9)
        //    /   |   \         /   \
        // (2 3) (4 5) (6 7)  (8)   (9)
        tree.put(null, new IntDataBox(5), new RecordId(5, (short) 5));
        ll = "((2 (2 2)) (3 (3 3)))";
        lm = "((4 (4 4)) (5 (5 5)))";
        lr = "((6 (6 6)) (7 (7 7)))";
        rl = "((8 (8 8)))";
        rr = "((9 (9 9)))";
        l = String.format("(%s 4 %s 6 %s)", ll, lm, lr);
        r = String.format("(%s 9 %s)", rl, rr);
        assertEquals(String.format("(%s 8 %s)", l, r), tree.toSexp(null));

        //                (4 8)
        //             /    |    \
        //       (3)        (6)         (9)
        //      /   \      /   \      /   \
        // (1 2) (3)   (4 5) (6 7) (8)   (9)
        tree.put(null, new IntDataBox(1), new RecordId(1, (short) 1));
        ll = "((1 (1 1)) (2 (2 2)))";
        lr = "((3 (3 3)))";
        String ml = "((4 (4 4)) (5 (5 5)))";
        String mr = "((6 (6 6)) (7 (7 7)))";
        rl = "((8 (8 8)))";
        rr = "((9 (9 9)))";
        l = String.format("(%s 3 %s)", ll, lr);
        m = String.format("(%s 6 %s)", ml, mr);
        r = String.format("(%s 9 %s)", rl, rr);
        assertEquals(String.format("(%s 4 %s 8 %s)", l, m, r), tree.toSexp(null));

        // Now remove keys. In an order 3 tree, a leaf needs at least
        // ceil(2/2) = 1 key, and a non-root inner node needs at least
        // ceil(3/2) = 2 pointers.

        //                (4 8)
        //             /    |    \
        //       (3)        (6)         (9)
        //      /   \      /   \      /   \
        // (2)   (3)   (4 5) (6 7) (8)   (9)
        tree.remove(null, new IntDataBox(1));
        ll = "((2 (2 2)))";
        lr = "((3 (3 3)))";
        ml = "((4 (4 4)) (5 (5 5)))";
        mr = "((6 (6 6)) (7 (7 7)))";
        rl = "((8 (8 8)))";
        rr = "((9 (9 9)))";
        l = String.format("(%s 3 %s)", ll, lr);
        m = String.format("(%s 6 %s)", ml, mr);
        r = String.format("(%s 9 %s)", rl, rr);
        assertEquals(String.format("(%s 4 %s 8 %s)", l, m, r), tree.toSexp(null));

        // The leaf (9) becomes empty and merges into the previous leaf (8).
        // Then (9) is left with 1 pointer and merges into the previous inner
        // node (6), with 8 pulled down from the root.
        //
        //            (4)
        //          /     \
        //       (3)        (6 8)
        //      /   \      /  |  \
        // (2)   (3)   (4 5) (6 7) (8)
        tree.remove(null, new IntDataBox(9));
        ll = "((2 (2 2)))";
        lr = "((3 (3 3)))";
        ml = "((4 (4 4)) (5 (5 5)))";
        String mm = "((6 (6 6)) (7 (7 7)))";
        mr = "((8 (8 8)))";
        l = String.format("(%s 3 %s)", ll, lr);
        m = String.format("(%s 6 %s 8 %s)", ml, mm, mr);
        assertEquals(String.format("(%s 4 %s)", l, m), tree.toSexp(null));

        // Removing 6 leaves 7 in its leaf, which is enough. The key 6 stays.
        //
        //            (4)
        //          /     \
        //       (3)        (6 8)
        //      /   \      /  |  \
        // (2)   (3)   (4 5) (7) (8)
        tree.remove(null, new IntDataBox(6));
        mm = "((7 (7 7)))";
        m = String.format("(%s 6 %s 8 %s)", ml, mm, mr);
        assertEquals(String.format("(%s 4 %s)", l, m), tree.toSexp(null));

        //            (4)
        //          /     \
        //       (3)        (6 8)
        //      /   \      /  |  \
        // (2)   (3)   (5)   (7) (8)
        tree.remove(null, new IntDataBox(4));
        ml = "((5 (5 5)))";
        m = String.format("(%s 6 %s 8 %s)", ml, mm, mr);
        assertEquals(String.format("(%s 4 %s)", l, m), tree.toSexp(null));

        // The leaf (2) becomes empty. It is the first child, so the next leaf
        // (3) merges into it. Then (3) is left with 1 pointer. The next inner
        // node (6 8) has 3 pointers, which do not fit together with it, so (3)
        // borrows the first pointer of (6 8), 4 moves down, and 6 moves up.
        //
        //            (6)
        //          /     \
        //       (4)        (8)
        //      /   \      /   \
        // (3)   (5)   (7)   (8)
        tree.remove(null, new IntDataBox(2));
        ll = "((3 (3 3)))";
        lr = "((5 (5 5)))";
        rl = "((7 (7 7)))";
        rr = "((8 (8 8)))";
        l = String.format("(%s 4 %s)", ll, lr);
        r = String.format("(%s 8 %s)", rl, rr);
        assertEquals(String.format("(%s 6 %s)", l, r), tree.toSexp(null));

        // The leaf (5) becomes empty and merges into the previous leaf (3).
        // Then (4) is left with 1 pointer, and the next inner node (8) merges
        // into it, with 6 pulled down from the root. The root is left with 1
        // pointer, so the merged node becomes the new root.
        //
        //      (6 8)
        //     /  |  \
        // (3)  (7)  (8)
        tree.remove(null, new IntDataBox(5));
        assertEquals(String.format("(%s 6 %s 8 %s)", ll, rl, rr), tree.toSexp(null));

        //    (8)
        //   /   \
        // (3)   (8)
        tree.remove(null, new IntDataBox(7));
        assertEquals(String.format("(%s 8 %s)", ll, rr), tree.toSexp(null));

        // The leaf (3) becomes empty, and the next leaf (8) merges into it. The
        // root is left with 1 pointer, so that leaf becomes the new root.
        tree.remove(null, new IntDataBox(3));
        assertEquals("((8 (8 8)))", tree.toSexp(null));

        // The root may have too few entries, so an empty root leaf is fine.
        tree.remove(null, new IntDataBox(8));
        assertEquals("()", tree.toSexp(null));
    }

    @Timeout(10)
    @Test
    public void testPut_random() throws BPlusTreeException, IOException {
        var keys = new ArrayList<DataBox>();
        var rids = new ArrayList<RecordId>();
        var sortedRids = new ArrayList<RecordId>();
        for (int i = 0; i < 1000; ++i) {
            keys.add(new IntDataBox(i));
            rids.add(new RecordId(i, (short) i));
            sortedRids.add(new RecordId(i, (short) i));
        }

        // Try trees with different orders, both even and odd.
        for (int n = 4; n <= 10; n += 3) {
            // Try trees with different insertion orders.
            for (int trial = 0; trial < 2; ++trial) {
                Collections.shuffle(keys, new Random(42));
                Collections.shuffle(rids, new Random(42));

                // Insert all the keys.
                BPlusTree tree = getBPlusTree(Type.intType(), n);
                for (int i = 0; i < keys.size(); ++i) {
                    tree.put(null, keys.get(i), rids.get(i));
                }

                // Test get.
                for (int i = 0; i < keys.size(); ++i) {
                    assertEquals(Optional.of(rids.get(i)), tree.get(null, keys.get(i)));
                }

                // Test scanAll.
                assertEquals(sortedRids, iteratorToList(tree.scanAll(null)));

                // Load the tree from disk.
                var fromDisk = new BPlusTree(file.toAbsolutePath().toString(), new DummyLockContext(), null);
                assertEquals(sortedRids, iteratorToList(fromDisk.scanAll(null)));

                // Test remove.
                Collections.shuffle(keys, new Random(42));
                Collections.shuffle(rids, new Random(42));
                for (int i = 0; i < keys.size(); ++i) {
                    fromDisk.remove(null, keys.get(i));
                    assertEquals(Optional.empty(), fromDisk.get(null, keys.get(i)));

                    // Halfway through, the remaining keys are still in order.
                    if (i == keys.size() / 2) {
                        var remaining = new ArrayList<RecordId>();
                        for (RecordId rid : sortedRids) {
                            if (!rids.subList(0, i + 1).contains(rid)) {
                                remaining.add(rid);
                            }
                        }
                        assertEquals(remaining, iteratorToList(fromDisk.scanAll(null)));
                    }
                }

                // With every key removed, the tree shrinks back to a single empty leaf.
                assertEquals("()", fromDisk.toSexp(null));
            }
        }
    }

    @Test
    public void testMaxOrder() {
        // Note that this white box test depend critically on the implementation
        // of toBytes and includes a lot of magic numbers that won't make sense
        // unless you read toBytes.
        assertEquals(4, Type.intType().getSizeInBytes());
        assertEquals(6, RecordId.getSizeInBytes());
        int pageSizeInBytes = 100;
        Type keySchema = Type.intType();
        assertEquals(10, LeafNode.maxOrder(pageSizeInBytes, keySchema));
        assertEquals(12, InnerNode.maxOrder(pageSizeInBytes, keySchema));
        assertEquals(10, BPlusTree.maxOrder(pageSizeInBytes, keySchema));
    }
}
