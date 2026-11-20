package edu.umd.cs424.database.query;

import edu.umd.cs424.database.Database;
import edu.umd.cs424.database.DatabaseException;
import edu.umd.cs424.database.TestUtils;
import edu.umd.cs424.database.TimeoutScaling;
import edu.umd.cs424.database.categories.ProjTests;
import edu.umd.cs424.database.categories.PublicTests;
import edu.umd.cs424.database.table.Record;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

@Category(ProjTests.class)
public class TestINLJOperator {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // 10 second max per method tested.
    @Rule
    public TestRule globalTimeout = new DisableOnDebug(Timeout.millis((long) (20000 * TimeoutScaling.factor)));

    Database.Transaction newTransaction() {
        return newTransaction(5);
    }

    Database.Transaction newTransaction(int numMemoryPages) {
        try {
            File tempDir = tempFolder.newFolder("indexNestedLoopJoinTest");
            return new Database(tempDir.getAbsolutePath(), numMemoryPages).beginTransaction();
        } catch (Exception e) {
            fail("An exception occurred. " + e.getMessage());
        }
        // unreachable
        return null;
    }

    @Test
    @Category(PublicTests.class)
    public void testINLJ_simple() throws DatabaseException, QueryPlanException {
        var transaction = newTransaction();
        var schema = TestUtils.createSchemaWithAllTypes();
        String left = "left", right = "right";

        transaction.createTableWithIndices(schema, left, Arrays.asList("int"));
        transaction.createTableWithIndices(schema, right, Arrays.asList("int"));

        int leftCount = 0;
        int rightCount = 0;

        for (int i = 0; i < 10; ++i, ++rightCount) {
            Record r = TestUtils.createRecordWithAllTypesWithValue(i);
            transaction.addRecord(left, r.getValues());
        }

        for (int i = 0; i < 10; ++i, ++leftCount, ++rightCount) {
            Record r = TestUtils.createRecordWithAllTypesWithValue(i + 10);
            transaction.addRecord(left, r.getValues());
            transaction.addRecord(right, r.getValues());
        }

        for (int i = 0; i < 10; ++i, ++rightCount) {
            Record r = TestUtils.createRecordWithAllTypesWithValue(i + 20);
            transaction.addRecord(right, r.getValues());
        }

        SequentialScanOperator leftOperator = new SequentialScanOperator(transaction, left);
        SequentialScanOperator rightOperator = new SequentialScanOperator(transaction, right);

        INLJOperator joinOperator = new INLJOperator(
                leftOperator,
                rightOperator /* Used only for computing output schema, not used for iteration during join */,
                "int",
                "int",
                transaction,
                right
        );
        var iter = joinOperator.iterator();

        int count = 0;
        var numFields = schema.getFieldTypes().size();

        while (iter.hasNext()) {
            var r = iter.next();
            for (int i = 0; i < numFields; ++i) {
                var leftValue = r.getValues().get(i);
                var rightValue = r.getValues().get(i + numFields);
                assertTrue(leftValue.compareTo(rightValue) == 0);
            }
            count++;
        }

        assertEquals(leftCount, count);

        transaction.close();
    }

}
