package edu.umd.cs424.database.index;

import edu.umd.cs424.database.databox.Type;
import edu.umd.cs424.database.io.PageAllocator;

/** Metadata about a B+ tree. */
class BPlusTreeMetadata {
    // The page allocator used to persist the B+ tree. Every node of the B+ tree
    // is stored on a single page allocated by this allocator.
    private final PageAllocator allocator;

    // B+ trees map keys (of some type) to record ids. This is the type of the
    // keys.
    private final Type keySchema;

    // The order n of the tree: the maximum number of pointers in a node, as in
    // the textbook. Given a tree of order n, its inner nodes store between
    // ceil(n/2) and n children pointers, i.e. between ceil(n/2) - 1 and n - 1
    // keys. Leaf nodes store between ceil((n-1)/2) and n-1 (key, record id)
    // pairs. Notable exceptions include the root node and leaf nodes that have
    // been deleted from; these may contain fewer entries. n must be at least 3.
    private final int order;

    public BPlusTreeMetadata(PageAllocator allocator, Type keySchema, int order) {
        this.allocator = allocator;
        this.keySchema = keySchema;
        this.order = order;
    }

    public PageAllocator getAllocator() {
        return allocator;
    }

    public Type getKeySchema() {
        return keySchema;
    }

    public int getOrder() {
        return order;
    }
}
