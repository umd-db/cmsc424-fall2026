# Project 3: B+ Trees

Project deadline: 11:59 PM on November 15, 2026.

This project is to be done by yourself.

## Introduction

To begin, pull the Project 3 folder from our repository as always.

This project was adapted from a B+ tree implementation project originally developed at the University of California Berkeley. The goal of this project is to give you a deeper understanding of how B+ trees work. The best way to accomplish this is by building one yourself. Do not fear, you will not be writing this from scratch!  Methods you will need to implement will have comments telling you what to do.  More on this later.

## Environment

### Using Your Editor/IDE of Choice

For this project, we highly recommend you install/use an IDE. Here are some popular options:
1. [IntelliJ](https://www.jetbrains.com/idea/download) (download the community edition). Open the project3 folder and choose "maven project" when prompted. You may also see a "JDK is not defined" warning when opening a file. Click "Setup JDK" and "Download JDK" to set the project up. IntelliJ is known to be slow during startup, so it may take a while for it to finish initializing.
2. [Eclipse](https://www.eclipse.org/downloads/). In Eclipse, import this project with: File > import > maven > existing maven project.
3. An editor of your choice such as VS Code. Note that you may need to manually [install OpenJDK](https://adoptium.net/temurin/releases/), [download maven](https://maven.apache.org/download.cgi), and [install maven](https://maven.apache.org/install.html) in order to run the project locally. Alternatively, you can skip installing maven and use the Maven wrapper in the project directory, which downloads maven for you: run `./mvnw compile` (Mac/Linux) or `mvnw.cmd compile` (Windows) wherever the instructions below say `mvn compile`. TAs may only be able to provide limited assistance if you choose this route since the setup process varies by person and by editor.

### Using Docker

Alternatively, you can run the project within a Docker environment and complete the project using an IDE/editor of your choice.

The docker container can be started with the following command. Make sure that the current directory is the directory of the project:

```bash
docker run -v $PWD:/project3 -ti --rm -w /project3 --pull missing maven:3.9.9-eclipse-temurin-21-alpine /bin/bash
```

In the terminal of the Docker container, you can compile the project by running

```bash
# build code without testing
mvn compile
```

You should be able to see "BUILD SUCCESS" in the output, indicating that your environment is set up correctly.

## Testing and Debugging
> [!NOTE]
> If you are eager to start writing code, you can skip this section for now and refer back here when testing and debugging your implementation.

### Docker

If you are using docker, simply run `mvn test` in the docker container to perform all tests. 

If you would like to run a specific test, you can run, for example
```bash
mvn test -Dtest=TestBPlusNode#testFromBytes
```
This will run `testFromBytes` in the `TestBPlusNode` class.

As the start of the project, `mvn test` will give out a bunch of error messages. This is normal behavior. You should be able to find a line of output that says something along the lines of

> Tests run: 29, Failures: 0, Errors: 27, Skipped: 0

Most tests failed because you have not yet implemented the functions they are testing. However, a few tests will pass out of the box.

Because the project directory and the docker container are synced, you can make edits to the project using your editor of choice. Your changes will be synced with the container so that running `mvn test` will test the latest changes you have made. However, debugging in this setup could be difficult. You are welcome to try other options such as developing with Docker through VS Code, though the TAs may only be able to provided limited assistance since the setup varies by person and editor.

### IDE or Editor

If you are using an IDE such as IntelliJ or Eclipse, you can navigate to the tests which reside under `src/test/java`, right-click on a test class (or a directory containing tests), and then choose `Run As > JUnit Test` (Eclipse) or `Run tests in Java` (IntelliJ).

Most tests will fail initially, but you may see a few successful tests: this is expected behavior.

If a test failed, and you are not sure why, you can insert a breakpoint in locations of interest and run individual tests that failed using a debugger. If you are not sure what the previous sentence is saying, you may want to learn more about how to use a debugger on your IDE/editor on your own.

## The Project Files

Here is a brief overview of the directories present in the project.

### index
The 'index' directory contains all the files that you will need to edit, specifically, LeafNode.java, InnerNode.java, and BPlusTree.java.  

* **BPlusNode.java**: An abstract class for LeafNode and InnerNode. This contains very important information on how to implement methods in those two classes.  Make sure you follow what is written here!

* **LeafNode.java**: Leaf nodes class.  You will need to implement some methods in this.

* **InnerNode.java**: Inner nodes class.  You will need to implement some methods in this.

* **BPlusTree.java**: B+ Tree class.  This is the structure that will hold LeafNodes and InnerNodes.  You will need to implement some methods in this.

> [!NOTE]
> Although there are a more files in this project, you do not need to have a robust understanding of all of them. 
> Knowing about how the remaining components work may be helpful but is not required to complete this project: you will only be changing code the index directory.

### common
The 'common' directory contains miscellaneous but handy bits of code.

### databox
The 'databox' directory contains classes which represent the values stored in a database as well as their types. Specifically, the DataBox class represents values and the Type class represents types. Here's an example:

```java
DataBox x = new IntDataBox(42); // The integer value '42'.
Type t = Type.intType();        // The type 'int'.
Type xsType = x.type();         // Get x's type: Type.intType()
int y = x.getInt();             // Get x's value: 42
String s = x.getString();       // An exception is thrown.
```

### io
The `io` directory contains code that allows you to allocate, read, and write
pages to and from a file. All modifications to the pages of the file are
persisted to the file. The two main classes of this directory are
`PageAllocator` which can be used to allocate pages in a file, and `Page` which
represents pages in the file.  Below are some examples of how this works so you can get an idea of it:

Here is an example of how to persist data into a file using a `PageAllocator`:

```java
// Create a page allocator which stores data in the file "foo.data". Setting
// wipe to true clears out any data that may have previously been in the file.
bool wipe = true;
PageAllocator allocator = new PageAllocator("foo.data", wipe);

// Allocate a page in the file. All pages are assigned a unique page number
// which can be used to fetch the page.
int pageNum = allocator.allocPage(); // The page number of the allocated page.
Page page = allocator.fetchPage(pageNum); // The page we just allocated.
System.out.println(pageNum); // 0. Page numbers are assigned 0, 1, 2, ...

// Write data into the page. All data written to the page is persisted in the
// file automatically.
Buffer buf = page.getBuffer(transaction);
buf.putInt(42);
buf.putInt(9001);
```

Here is an example of how to read data that's been persisted to a file:

```java
// Create a page allocator which stores data in the file "foo.data". Setting
// wipe to false means that this page allocator can read any data that was
// previously stored in "foo.data".
bool wipe = false;
PageAllocator allocator = new PageAllocator("foo.data", wipe);

// Fetch the page we previously allocated.
Page page = allocator.fetchPage(0);

// Read the data we previously wrote.
Buffer buf = page.getBuffer(transaction);
int x = buf.getInt(); // 42
int y = buf.getInt(); // 9001
```

### table
The 'table' directory used to contain other table related things but you only need the 'RecordId' class which uniquely identifies a record on a page by its page number and entry number:

```java
// The jth record on the ith page.
RecordId rid = new RecordId(i, (short) j);
```

There are a few other files that are used to make the tests work that you are welcome to look at if you need to.

## Your Tasks
Familiarize yourself with the code in the 'index' directory since all the changes that you will make will happen here.

### First Function
Implement the `LeafNode::toBytes` function that serializes a `LeafNode` into a byte array. This byte array will be used by some caller to write the node to disk. The format of the serialization is described in the comments of the `LeafNode::toBytes` function. For an example of how a node is written to disk, see `InnerNode::toBytes`.

Implement the `LeafNode::fromBytes` function that deserializes a `LeafNode` from a page. You should refer to the `LeafNode::toBytes` function for the format of the serialization. For an example on how to read a node from disk, see `InnerNode::fromBytes`.

Our implementation of B+ trees assumes that inner nodes and leaf nodes can be serialized on a single page. You do not have to support nodes that span multiple pages.

> [!NOTE]
> It is important to implement this function correctly first because many other methods and tests depend on it. 

After implementing this function, the test `TestBPlusNode::testFromBytes` should pass. For more information on running tests and debugging your code when a test fails, refer back to the [testing and debugging section](#testing-and-debugging).

### Node Functions
Implement the `get` and `getLeftmostLeaf` methods of `LeafNode` and `InnerNode`. For information on what these methods do, refer to the comments in `BPlusNode`.

> [!NOTE]
> You may not modify the signature of any methods or classes that we provide to you, but you're free to add helper methods in LeafNode.java, InnerNode.java, and BPlusTree.java. This applies to all parts of the project.

All tests prefixed by "testGet" in `TestInnerNode` and `TestLeafNode` should pass at this point. 

---

Implement the `put` and `bulkLoad` methods of `LeafNode` and `InnerNode`. For information on what these methods do, refer to the comments in `BPlusNode`.

> [!IMPORTANT]
> Our implementation of B+ trees does not support duplicate keys. You need to throw a `BPlusTreeException` whenever a duplicate key is inserted.
> 
> The order `n` of a B+ Tree is given upon the tree's creation within its metadata field which is passed onto its inner nodes and leaf nodes. Refer to the [things to note section](#things-to-note) for more information.
> 
> Split nodes exactly as in the lecture slides. When a leaf overflows, the first `ceil(n/2)` entries stay in the left leaf and the rest move to the new right leaf. When an inner node overflows, the first `ceil((n+1)/2)` pointers stay in the left node, the rest move to the new right node, and the key between them moves up. See the comments in `BPlusNode` for examples.
> 
> Don't forget to call `sync` when implementing `put`, `remove`, and `bulkLoad`.

Again, all tests prefixed by `testPut` or `testBulkLoad` in `TestInnerNode` and `TestLeafNode` should pass.

---

Implement `remove` in `LeafNode` and `InnerNode`.

> [!IMPORTANT]
> Rebalance the tree after deletion exactly as in the lecture slides. `remove` returns `true` when the node it was called on now has too few entries (see the [node size table](#things-to-note)), and the parent then fixes that child using its previous sibling, or its next sibling if the child is the first child:
>
> - If the entries of the two nodes fit in a single node, merge the right node into the left node, and remove the key between them and the pointer to the right node from the parent.
> - Otherwise, move exactly one entry from the sibling into the child, and update the key between them in the parent.
>
> The root may have too few entries, but when an inner root is left with a single pointer, its only child becomes the new root. See the comments in `BPlusNode.remove` for the exact rules and an example.

All tests in `TestBPlusNode`, `TestInnerNode`, and `TestLeafNode` should pass at this point. Again, the next set of functionalities depend on methods listed above, so you are recommended to make sure everything is correctly implemented before proceeding.

### B+ Tree Functions
Implement the `get`, `put`, `remove`, and `bulkLoad` methods of `BPlusTree`. 

All tests should pass at this point.

## Things to Note

Like the lecture slides and the textbook, this project uses a variable `n` to describe the "order" of a B+ Tree. `n` is the maximum number of **pointers** in a node, so a node holds at most `n - 1` **values** (keys). This is true both for leaf nodes and internal nodes: every node has one more pointer than value. In this project, `n` can be any integer that is at least 3, odd or even.

As in the textbook,
> Each leaf can hold up to n − 1 values. We allow leaf nodes to contain as few as ceiling[ (n−1)/2 ] values.

For example, a node with `n = 4` has room for 4 pointers and 3 values, and a node with `n = 5` has room for 5 pointers and 4 values:

| | Formula | `n = 4` | `n = 5` |
|---|---|---|---|
| Max. #pointers | `n` | 4 | 5 |
| Max. #values | `n - 1` | 3 | 4 |
| Min. #values (non-root leaf) | `ceil((n-1)/2)` | 2 | 2 |
| Min. #pointers (non-root inner node) | `ceil(n/2)` | 2 | 3 |
| Min. #values (root leaf) | `0` | 0 | 0 |
| Min. #pointers (root inner node) | `2` | 2 | 2 |

The root is the exception: as a leaf it may hold any number of values from 0 to `n - 1`, and as an inner node it needs only 2 pointers.

In the code, `n` is returned by `metadata.getOrder()`.

## Submitting
Just submit your `LeafNode.java`, `InnerNode.java`, and `BPlusTree.java` to Gradescope.
