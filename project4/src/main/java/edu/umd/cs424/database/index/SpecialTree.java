package edu.umd.cs424.database.index;

import edu.umd.cs424.database.BaseTransaction;
import edu.umd.cs424.database.concurrency.LockContext;
import edu.umd.cs424.database.databox.DataBox;
import edu.umd.cs424.database.databox.Type;
import edu.umd.cs424.database.table.RecordId;

import java.io.Closeable;
import java.util.*;

public class SpecialTree implements Closeable {
    private TreeMap<DataBox, RecordId> index = new TreeMap<>();

    public static final String FILENAME_PREFIX = "db";
    public static final String FILENAME_EXTENSION = ".index";

    public SpecialTree(String filename, Type keySchema, int order, LockContext lockContext,
                       BaseTransaction transaction)
            throws BPlusTreeException {
    }

    public SpecialTree(String filename, LockContext lockContext, BaseTransaction transaction) {
    }

    public void close() {
    }

    public Optional<RecordId> get(BaseTransaction transaction, DataBox key) {
        RecordId x = index.get(key);
        return x == null ? Optional.empty() : Optional.of(x);
    }

    public Iterator<RecordId> scanEqual(BaseTransaction transaction, DataBox key) {
        Optional<RecordId> rid = get(transaction, key);
        if (rid.isPresent()) {
            ArrayList<RecordId> l = new ArrayList<>();
            l.add(rid.get());
            return l.iterator();
        } else {
            return new ArrayList<RecordId>().iterator();
        }
    }

    public Iterator<RecordId> scanAll(BaseTransaction transaction) {
        return Collections.<RecordId>emptyIterator();
    }

    public Iterator<RecordId> scanGreaterEqual(BaseTransaction transaction, DataBox key) {
        return Collections.<RecordId>emptyIterator();
    }

    public void put(BaseTransaction transaction, DataBox key, RecordId rid) throws BPlusTreeException {
        index.put(key, rid);
    }

    public void remove(BaseTransaction transaction, DataBox key) {
        index.remove(key);
    }

    public int getNumPages() {
        return -1;
    }

    public static int maxOrder(int pageSizeInBytes, Type keySchema) {
        return -1;
    }

    // Iterator ////////////////////////////////////////////////////////////////
    private class SpecialTreeIterator implements Iterator<RecordId> {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public RecordId next() {
            throw new NoSuchElementException();
        }
    }
}
