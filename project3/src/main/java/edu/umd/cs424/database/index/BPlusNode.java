package edu.umd.cs424.database.index;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import edu.umd.cs424.database.BaseTransaction;
import edu.umd.cs424.database.common.Buffer;
import edu.umd.cs424.database.common.Pair;
import edu.umd.cs424.database.databox.DataBox;
import edu.umd.cs424.database.io.Page;
import edu.umd.cs424.database.table.RecordId;

/**
 * An inner node or a leaf node. See InnerNode and LeafNode for more
 * information.
 */
abstract class BPlusNode {
    // Core API //////////////////////////////////////////////////////////////////
    /**
     * node.get(k) returns the leaf node on which k may reside when queried from this node.
     * For example, consider the following order 5 B+ tree (each node holds at
     * most n-1 = 4 keys; for brevity, only keys are shown; record ids are
     * ommitted).
     *
     *                               inner
     *                               +----+----+----+----+
     *                               | 10 | 20 |    |    |
     *                               +----+----+----+----+
     *                              /     |     \
     *                         ____/      |      \____
     *                        /           |           \
     *   +----+----+----+----+  +----+----+----+----+  +----+----+----+----+
     *   |  1 |  2 |  3 |    |->| 11 | 12 | 13 |    |->| 21 | 22 | 23 |    |
     *   +----+----+----+----+  +----+----+----+----+  +----+----+----+----+
     *   leaf0                  leaf1                  leaf2
     *
     * inner.get(x) should return
     *
     *   - leaf0 when x < 10,
     *   - leaf1 when 10 <= x < 20, and
     *   - leaf2 when x >= 20.
     *
     * Note that inner.get(4) would return leaf0 even though leaf0 doesn't
     * actually contain 4.
     */
    public abstract LeafNode get(BaseTransaction transaction, DataBox key);

    /**
     * node.getLeftmostLeaf() returns the leftmost leaf in the subtree rooted by this node.
     * In the example above, inner.getLeftmostLeaf() would return leaf0, and
     * leaf1.getLeftmostLeaf() would return leaf1.
     */
    public abstract LeafNode getLeftmostLeaf(BaseTransaction transaction);

    /**
     * node.put(k, r) inserts the pair (k, r) into the subtree rooted by this node. There
     * are two cases to consider:
     *
     *   Case 1: If inserting the pair (k, r) does NOT cause the node to overflow, then
     *           Optional.empty() is returned.
     *   Case 2: If inserting the pair (k, r) does cause the node to overflow,
     *           then the node is split into a left and right node (described more
     *           below) and a pair (split_key, right_node_page_num) is returned
     *           where right_node_page_num is the page number of the newly
     *           created right node, and the value of split_key depends on
     *           whether the node is an inner node or a leaf node (described more below).
     *
     * Now we explain how to split nodes and which split keys to return. Let's
     * take a look at an example. Consider inserting the key 4 into the example
     * tree above. No nodes overflow (i.e. we always hit case 1). The tree then
     * looks like this:
     *
     *                               inner
     *                               +----+----+----+----+
     *                               | 10 | 20 |    |    |
     *                               +----+----+----+----+
     *                              /     |     \
     *                         ____/      |      \____
     *                        /           |           \
     *   +----+----+----+----+  +----+----+----+----+  +----+----+----+----+
     *   |  1 |  2 |  3 |  4 |->| 11 | 12 | 13 |    |->| 21 | 22 | 23 |    |
     *   +----+----+----+----+  +----+----+----+----+  +----+----+----+----+
     *   leaf0                  leaf1                  leaf2
     *
     * Now let's insert key 5 into the tree. Now, leaf0 overflows and creates a
     * new right sibling leaf3. As in the lecture slides, the overflowing leaf
     * temporarily holds n entries; the first ceil(n/2) entries remain in the
     * left node and the remaining floor(n/2) entries are moved to the right
     * node. DO NOT REDISTRIBUTE ENTRIES ANY OTHER WAY. In our example (n = 5),
     * leaf0 and leaf3 would look like this:
     *
     *   +----+----+----+----+  +----+----+----+----+
     *   |  1 |  2 |  3 |    |->|  4 |  5 |    |    |
     *   +----+----+----+----+  +----+----+----+----+
     *   leaf0                  leaf3
     *
     * When a leaf splits, it returns the first entry in the right node as the
     * split key. In this example, 4 is the split key. After leaf0 splits, inner
     * inserts the new key and child pointer into itself and hits case 1 (i.e. it
     * does not overflow). The tree looks like this:
     *
     *                          inner
     *                          +--+--+--+--+
     *                          | 4|10|20|  |
     *                          +--+--+--+--+
     *                         /   |  |   \
     *                 _______/    |  |    \_________
     *                /            |   \             \
     *   +--+--+--+--+  +--+--+--+--+  +--+--+--+--+  +--+--+--+--+
     *   | 1| 2| 3|  |->| 4| 5|  |  |->|11|12|13|  |->|21|22|23|  |
     *   +--+--+--+--+  +--+--+--+--+  +--+--+--+--+  +--+--+--+--+
     *   leaf0          leaf3          leaf1          leaf2
     *
     * When an inner node splits, the overflowing node temporarily holds n keys
     * and n+1 child pointers. As in the lecture slides, the first
     * ceil((n+1)/2) pointers (and the ceil((n+1)/2) - 1 keys between them) are
     * kept in the left node and the remaining pointers and keys are moved to the
     * right node. The key between the two groups of pointers is moved (not
     * copied) up as the split key. For example, we would split the following
     * order 5 inner node
     *
     *   +---+---+---+---+
     *   | 1 | 2 | 3 | 4 | 5
     *   +---+---+---+---+
     *
     * into the following two inner nodes
     *
     *   +---+---+---+---+  +---+---+---+---+
     *   | 1 | 2 |   |   |  | 4 | 5 |   |   |
     *   +---+---+---+---+  +---+---+---+---+
     *
     * with a split key of 3. Similarly, in an order 4 tree, the overflowing
     * inner node
     *
     *   +---+---+---+
     *   | 1 | 2 | 3 | 4
     *   +---+---+---+
     *
     * keeps ceil(5/2) = 3 pointers, and so splits into
     *
     *   +---+---+---+  +---+---+---+
     *   | 1 | 2 |   |  | 4 |   |   |
     *   +---+---+---+  +---+---+---+
     *
     * with a split key of 3.
     *
     * DO NOT redistribute entries in any other way besides what we have
     * described. For example, do not move entries between nodes to avoid
     * splitting.
     *
     * Our B+ trees do not support duplicate entries with the same key. If a
     * duplicate key is inserted, the tree is left unchanged and an exception is
     * raised.
     */
    public abstract Optional<Pair<DataBox, Integer>> put(BaseTransaction transaction, DataBox key,
            RecordId rid)
    throws BPlusTreeException;

    /**
     * node.bulkLoad(data, fillFactor) bulk loads pairs of (k, r) from data into
     * the tree with the given fill factor.
     *
     * This method is very similar to node.put, with a couple of differences:
     *
     * 1. Leaf nodes do not fill up to n entries and split, but rather, fill up to
     * be 1 record more than fillFactor full, then "splits" by creating a right
     * sibling that contains just one record (leaving the original node with
     * the desired fill factor).
     *
     * 2. Inner nodes should repeatedly try to bulk load the rightmost child
     * until either the inner node is full (in which case it should split)
     * or there is no more data.
     *
     * fillFactor should ONLY be used for determining how full leaf nodes are
     * (not inner nodes), and calculations should round up, i.e. with n=11
     * and fillFactor=0.75, leaf nodes should hold ceil(0.75 * (n-1)) = 8 of
     * their n-1 = 10 entries.
     */
    public abstract Optional<Pair<DataBox, Integer>> bulkLoad(BaseTransaction transaction,
            Iterator<Pair<DataBox, RecordId>> data,
            float fillFactor)
    throws BPlusTreeException;

    /**
     * node.remove(k) removes the key k and its corresponding record id from the
     * subtree rooted by this node, or does nothing if the key k is not in the
     * subtree. As in the lecture slides, the tree is rebalanced when a node ends
     * up with too few entries.
     *
     * remove returns true if it removed an entry from this node and this node
     * now has too few entries, and false otherwise. In a B+ tree of order n, a
     * node has too few entries when
     *
     *   - it is a leaf with fewer than ceil((n-1)/2) keys, or
     *   - it is an inner node with fewer than ceil(n/2) pointers.
     *
     * A node never fixes itself. When child.remove(k) returns true, the parent
     * inner node fixes the child with the help of one of its siblings. As in
     * the lecture slides' delete_entry, we call the child N, the sibling N',
     * and the key between the pointers to N and N' in the parent K'. N' is the
     * previous child of the parent (the one just to the left of N), or the next
     * child if N is the first child. There are two cases to consider:
     *
     *   Case 1 (merge): If the entries of N and N' fit in a single node (at most
     *           n-1 keys for leaves, at most n pointers for inner nodes), then
     *           the right node of the two is merged into the left node and the
     *           right node is removed from the tree. For leaves, all pairs of
     *           the right leaf are appended to the left leaf, and the left
     *           leaf's right sibling is set to the right leaf's right sibling.
     *           For inner nodes, K' and then all keys and pointers of the
     *           right node are appended to the left node. Either way, the
     *           parent then removes K' and its pointer to the right node.
     *
     *   Case 2 (borrow): Otherwise, N borrows exactly one entry from N'.
     *
     *           - Leaf, N' on the left: the last pair of N' moves to the front
     *             of N, and K' is replaced by the key of that pair.
     *           - Leaf, N' on the right: the first pair of N' moves to the end
     *             of N, and K' is replaced by the new first key of N'.
     *           - Inner, N' on the left: the last pointer of N' moves to the
     *             front of N, K' moves down into N as its first key, and the
     *             last key of N' moves up to replace K'.
     *           - Inner, N' on the right: the first pointer of N' moves to the
     *             end of N, K' moves down into N as its last key, and the first
     *             key of N' moves up to replace K'.
     *
     * A merge removes a key and a pointer from the parent, so the parent may now
     * have too few entries itself, in which case its own remove returns true
     * and its parent fixes it in the same way. The root is the exception: it
     * is allowed to have fewer entries, and BPlusTree.remove ignores what the
     * root's remove returns. Instead, if the root is an inner node with only
     * one pointer left, its only child becomes the new root.
     *
     * Keys in inner nodes change only as described above. In particular, if k
     * also appears as a key in an inner node, that key stays where it is. You
     * do not need to free the page of a node removed by a merge.
     *
     * For example, consider the following order 5 tree. A leaf needs at least
     * ceil(4/2) = 2 keys, and a non-root inner node needs at least ceil(5/2) = 3
     * pointers.
     *
     *                          inner
     *                          +--+--+--+--+
     *                          |10|20|  |  |
     *                          +--+--+--+--+
     *                         /   |   \
     *                ________/    |    \________
     *               /             |             \
     *   +--+--+--+--+  +--+--+--+--+  +--+--+--+--+
     *   | 1| 2|  |  |->|11|12|13|14|->|21|22|23|  |
     *   +--+--+--+--+  +--+--+--+--+  +--+--+--+--+
     *   leaf0          leaf1          leaf2
     *
     * Running inner.remove(1) leaves leaf0 with only the key 2, which is too
     * few, so leaf0.remove(1) returns true. leaf0 is the first child, so its
     * sibling is the next child, leaf1, and K' is 10. The 1 + 4 = 5 keys do not
     * fit in a single leaf, so leaf0 borrows 11 from leaf1, and 10 is replaced
     * by the new first key of leaf1, 12. inner still has 3 pointers, so
     * inner.remove(1) returns false.
     *
     *                          inner
     *                          +--+--+--+--+
     *                          |12|20|  |  |
     *                          +--+--+--+--+
     *                         /   |   \
     *                ________/    |    \________
     *               /             |             \
     *   +--+--+--+--+  +--+--+--+--+  +--+--+--+--+
     *   | 2|11|  |  |->|12|13|14|  |->|21|22|23|  |
     *   +--+--+--+--+  +--+--+--+--+  +--+--+--+--+
     *   leaf0          leaf1          leaf2
     *
     * Running inner.remove(21) and then inner.remove(22) leaves leaf2 with only
     * the key 23. Its sibling is the previous child, leaf1, and K' is 20. The
     * 3 + 1 = 4 keys fit in a single leaf, so leaf2 is merged into leaf1, and
     * inner removes 20 and its pointer to leaf2. Now inner has only 2 pointers,
     * so inner.remove(22) returns true, and inner's parent would fix inner (if
     * inner were the root, it would simply stay as it is).
     *
     *                 inner
     *                 +--+--+--+--+
     *                 |12|  |  |  |
     *                 +--+--+--+--+
     *                /   |
     *       ________/    |
     *      /             |
     *   +--+--+--+--+  +--+--+--+--+
     *   | 2|11|  |  |->|12|13|14|23|
     *   +--+--+--+--+  +--+--+--+--+
     *   leaf0          leaf1
     *
     * DO NOT rebalance in any other way. For example, do not borrow when you
     * can merge, and do not borrow more than one entry.
     */
    public abstract boolean remove(BaseTransaction transaction, DataBox key);

    // Helpers ///////////////////////////////////////////////////////////////////
    /** Get the page on which this node is persisted. */
    abstract Page getPage();

    // Pretty Printing ///////////////////////////////////////////////////////////
    /**
     * S-expressions (or sexps) are a compact way of encoding nested tree-like
     * structures (sort of like how JSON is a way of encoding nested dictionaries
     * and lists). n.toSexp() returns an sexp encoding of the subtree rooted by
     * n. For example, the following tree:
     *
     *                      +---+
     *                      | 3 |
     *                      +---+
     *                     /     \
     *   +---------+---------+  +---------+---------+
     *   | 1:(1 1) | 2:(2 2) |  | 3:(3 3) | 4:(4 4) |
     *   +---------+---------+  +---------+---------+
     *
     * has the following sexp
     *
     *   (((1 (1 1)) (2 (2 2))) 3 ((3 (3 3)) (4 (4 4))))
     *
     * Here, (1 (1 1)) represents the mapping from key 1 to record id (1, 1).
     */
    public abstract String toSexp(BaseTransaction transaction);

    /**
     * node.toDot() returns a fragment of a DOT file that draws the subtree rooted
     * at this node.
     */
    public abstract String toDot(BaseTransaction transaction);

    // Serialization /////////////////////////////////////////////////////////////
    /** node.toBytes() serializes the node. */
    public abstract byte[] toBytes();

    /**
     * BPlusNode.fromBytes(m, p) loads a BPlusNode from page p of
     * meta.getAllocator().
     */
    public static BPlusNode fromBytes(BaseTransaction transaction, BPlusTreeMetadata metadata,
                                      int pageNum) {
        Page p = metadata.getAllocator().fetchPage(transaction, pageNum);
        Buffer buf = p.getBuffer(transaction);
        byte b = buf.get();
        if (b == 1) {
            return LeafNode.fromBytes(transaction, metadata, pageNum);
        } else if (b == 0) {
            return InnerNode.fromBytes(transaction, metadata, pageNum);
        } else {
            String msg = String.format("Unexpected byte %b.", b);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Given a list ys sorted in ascending order, numLessThanEqual(x, ys) returns
     * the number of elements in ys that are less than or equal to x. For
     * example,
     *
     *   numLessThanEqual(0, Arrays.asList(1, 2, 3, 4, 5)) == 0
     *   numLessThanEqual(1, Arrays.asList(1, 2, 3, 4, 5)) == 1
     *   numLessThanEqual(2, Arrays.asList(1, 2, 3, 4, 5)) == 2
     *   numLessThanEqual(3, Arrays.asList(1, 2, 3, 4, 5)) == 3
     *   numLessThanEqual(4, Arrays.asList(1, 2, 3, 4, 5)) == 4
     *   numLessThanEqual(5, Arrays.asList(1, 2, 3, 4, 5)) == 5
     *   numLessThanEqual(6, Arrays.asList(1, 2, 3, 4, 5)) == 5
     *
     * This helper function is useful when we're navigating down a B+ tree and
     * need to decide which child to visit. For example, imagine an index node
     * with the following 4 keys and 5 children pointers:
     *
     *     +---+---+---+---+
     *     | a | b | c | d |
     *     +---+---+---+---+
     *    /    |   |   |    \
     *   0     1   2   3     4
     *
     * If we're searching the tree for value c, then we need to visit child 3.
     * Not coincidentally, there are also 3 values less than or equal to c (i.e.
     * a, b, c).
     */
    public static <T extends Comparable<T>> int numLessThanEqual(T x, List<T> ys) {
        int k = 0;
        for (T y : ys) {
            if (y.compareTo(x) <= 0) {
                ++k;
            } else {
                break;
            }
        }
        return k;
    }

    /** Same as numLessThanEqual but for < instead of <= */
    public static <T extends Comparable<T>> int numLessThan(T x, List<T> ys) {
        int k = 0;
        for (T y : ys) {
            if (y.compareTo(x) < 0) {
                ++k;
            } else {
                break;
            }
        }
        return k;
    }

    /** Serializes this node to its page. */
    protected void sync(BaseTransaction transaction) {
        Buffer b = getPage().getBuffer(transaction);
        byte[] newBytes = toBytes();
        byte[] bytes = new byte[newBytes.length];
        b.get(bytes);
        if (!Arrays.equals(bytes, newBytes)) {
            getPage().getBuffer(transaction).put(toBytes());
        }
    }
}
