package concurrentcube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;


public class CubeTest {

    /**
     * It tests for rotations correctness.
     * <p>
     * If this test fails it means that rotations are not implemented properly.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 100, 201, 512})
    @DisplayName("Tests if sequential cyclic rotations change cube's initial state.")
    void testSequentialCycleRotations(int size) throws InterruptedException {
        int numCyclicRotations = 1000;

        // Create a cube of various sizes.
        Cube cube = cubeWithDummyWork(size, 0);

        // Get initial cube's state.
        String cubeBeforeCyclicRotations = cube.show();

        Random rand = new Random();
        for (int i = 0; i < numCyclicRotations; i++) {
            // Pick random side and layer for the cyclic rotation.
            int side = rand.nextInt(Cube.getNumSides());
            int layer = rand.nextInt(size);
            // Rotate the cube four times by the same rotation.
            for (int j = 0; j < 4; j++) {
                cube.rotate(side, layer);
            }
        }

        // Get cube's state after many cyclic rotations and assert that it is in the same state as before.
        String cubeAfterCyclicRotations = cube.show();
        assertEquals(cubeBeforeCyclicRotations, cubeAfterCyclicRotations);
    }


    /**
     * It tests for rotations correctness.
     * <p>
     * If this test fails it means that rotations are not implemented properly.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 100, 201})
    @DisplayName("Tests if sequential rotations work properly on one magic rotations sequence.")
    void testSequentialSequenceOfRotations(int size) throws InterruptedException {
        // Create a cube of various sizes.
        Cube cube = cubeWithDummyWork(size, 0);

        // Get initial cube's state.
        String cubeBeforeCyclicRotations = cube.show();

        // Regardless of the size of the cube, the following sequence of moves, repeated 1260 times must yield initial cube's state.
        // https://en.wikipedia.org/wiki/Rubik%27s_Cube_group
        for (int i = 0; i < 1260; i++) {
            cube.rotate(3, 0);
            cube.rotate(0, 0);
            cube.rotate(0, 0);
            cube.rotate(5, 0);
            cube.rotate(5, 0);
            cube.rotate(5, 0);
            cube.rotate(4, 0);
            cube.rotate(5, 0);
            cube.rotate(5, 0);
            cube.rotate(5, 0);
        }
        String cubeAfterCyclicRotations = cube.show();
        assertEquals(cubeBeforeCyclicRotations, cubeAfterCyclicRotations);
    }


    /**
     * It tests for rotations correctness and thread-safety.
     * <p>
     * If this test fails it means that rotations are not implemented properly or conflicting writes were done simultaneously.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 4, 8, 15, 32})
    @DisplayName("Tests if random concurrent cyclic rotation change cube's initial state.")
    void testConcurrentThreadsSameRotation(int size) throws InterruptedException {
        int numberOfRuns = 100;
        int numberOfTasks = 100;
        int numberofThreads = 32;
        int dummyWork = 1000000;

        for (int i = 0; i < numberOfRuns; i++) {
            // Create a cube of various sizes.
            Cube cube = cubeWithDummyWork(size, dummyWork);

            // Get initial cube's state.
            String cubeBeforeConcurrentCyclicRotations = cube.show();

            // Create a list for futures and a pool of threads.
            List<Future<?>> futures = new ArrayList<>();
            ExecutorService taskExecutorThreads = Executors.newFixedThreadPool(numberofThreads);

            // Randomly pick a side along which rotations will be performed.
            Random random = new Random();
            int randomSide = random.nextInt(Cube.getNumSides());

            for (int j = 0; j < numberOfTasks; j++) {
                futures.add(taskExecutorThreads.submit(() -> {
                    // Randomly pick a layer along which rotations will be performed.
                    int randomLayer = random.nextInt(size);
                    try {
                        cube.rotate(randomSide, randomLayer);
                        cube.rotate(randomSide, randomLayer);
                        cube.rotate(randomSide, randomLayer);
                        cube.rotate(randomSide, randomLayer);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));
            }

            // Wait until all tasks are done.
            returnWhenAllFuturesAreCompleted(futures);

            // Get cube's state after many cyclic rotations and assert that it is in the same state as before.
            String cubeAfterConcurrentCyclicRotations = cube.show();
            assertEquals(cubeBeforeConcurrentCyclicRotations, cubeAfterConcurrentCyclicRotations);
        }
    }


    /**
     * It tests for thread-safety.
     * <p>
     * If this test fails it means that the solution is not thread-safe either because:
     * the cube was shown in an inconsistent state while being updated,
     * or the writes were done by conflicting writers at the same time and spoiled color consistency.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 4, 8, 15, 32})
    @DisplayName("Tests if cube is always shown in a consistent state - number of squares of each color is equal.")
    void testIfStateIsValidDuringConcurrentRotations(int size) throws InterruptedException {
        int numberOfRuns = 10;
        int numberOfTasks = 1000;
        int numberofThreads = 8;
        int dummyWork = 1000000;

        for (int i = 0; i < numberOfRuns; i++) {
            // Create a cube of various sizes.
            Cube cube = cubeWithDummyWork(size, dummyWork);

            // Create a list for futures and a pool of threads.
            List<Future<?>> futures = new ArrayList<>();
            ExecutorService taskExecutorThreads = Executors.newFixedThreadPool(numberofThreads);

            // Randomly pick a side along which rotations will be performed.
            Random random = new Random();
            int randomSide = random.nextInt(Cube.getNumSides());

            for (int j = 0; j < numberofThreads; j++) {
                futures.add(taskExecutorThreads.submit(() -> {
                    for (int k = 0; k < numberOfTasks; k++) {
                        // Randomly pick a layer along which rotations will be performed.
                        int randomLayer = random.nextInt(size);
                        try {
                            cube.rotate(randomSide, randomLayer);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }));
            }

            // Create a thread which terminates as soon as all cube's rotations are done.
            Thread completionCheckerThread = new Thread(() -> returnWhenAllFuturesAreCompleted(futures));
            completionCheckerThread.start();

            while (completionCheckerThread.isAlive()) {
                // Check cube's state while rotations are being done on the cube.
                assertTrue(validateCubeByColorFrequency(cube));
            }
        }
    }


    /**
     * It tests for non-conflicting writes concurrency.
     * <p>
     * If this test fails it means that non-conflicting writes were not concurrent, because:
     * the number of unique write versions was equal to the number of writes.
     */
    @ParameterizedTest
    @ValueSource(ints = {4, 8, 15, 32})
    @DisplayName("Tests if non-conflicting writes are done concurrently.")
    void testNonConflictingWrites(int size) throws InterruptedException {
        int numberOfConcurrentThreads = 512;
        int numberOfWritesPerThread = 4;

        // Create variable which helps to determine if non-conflicting writes are concurrent.
        AtomicInteger versionCounter = new AtomicInteger(0);
        List<Integer> versionList = Collections.synchronizedList(new ArrayList<>());

        // Create a function which adds current versionCounter to a list.
        BiConsumer<Integer, Integer> beforeRotation = new BiConsumer<Integer, Integer>() {
            @Override
            public void accept(Integer integer, Integer integer2) {
                try {
                    TimeUnit.MILLISECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                versionList.add(versionCounter.get());
            }
        };

        // Create a function which increases the versionCounter after performing a write.
        BiConsumer<Integer, Integer> afterRotation = new BiConsumer<Integer, Integer>() {
            @Override
            public void accept(Integer integer, Integer integer2) {
                try {
                    TimeUnit.MILLISECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                versionCounter.addAndGet(1);
            }
        };

        // Create a cube with writes versioning.
        Cube cube = new Cube(size, beforeRotation, afterRotation, () -> {
        }, () -> {
        });

        // Create writing threads.
        List<Thread> threads = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < numberOfConcurrentThreads; i++) {
            // Each thread will perform random writes.
            threads.add(new Thread(() -> {
                for (int j = 0; j < numberOfWritesPerThread; j++) {
                    try {
                        cube.rotate(random.nextInt(Cube.getNumSides()), random.nextInt(size));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        // Get unique versions of concurrent writes.
        Set<Integer> uniqueVersions = new HashSet<>(versionList);

        // Assert that the operations were done concurrently.
        float concurrencyCoefficient = (float) numberOfConcurrentThreads * numberOfWritesPerThread / uniqueVersions.size();
        assertTrue(concurrencyCoefficient > 1);
    }


    /**
     * It tests for liveness of read operations.
     * <p>
     * If this test fails it means that there was no liveness of read operations, because:
     * the reading thread was not allowed to get the cube's state while there was a heavy stream of write operations.
     */
    @ParameterizedTest
    @ValueSource(ints = {4, 8, 15, 32})
    @DisplayName("Tests if there is liveness of read operations.")
    void testLivenessOfReads(int size) {
        int numberOfRuns = 10;
        int numberOfWritingThreads = 10;
        int timeoutSeconds = 5;
        Random random = new Random();

        for (int i = 0; i < numberOfRuns; i++) {
            Cube cube = cubeWithDummyWork(size, 0);

            // Create a collection of threads which perform infinite stream of random writes.
            List<Thread> threads = new ArrayList<>();
            for (int j = 0; j < numberOfWritingThreads; j++) {
                int randomSide = random.nextInt(Cube.getNumSides());
                int randomLayer = random.nextInt(size);
                threads.add(new Thread(() -> {
                    while (true) {
                        try {
                            cube.rotate(randomSide, randomLayer);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }));
            }

            for (Thread t : threads) {
                t.start();
            }

            // Create a reading thread.
            Thread readingThread = new Thread(() -> {
                try {
                    cube.show();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            readingThread.start();
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() < startTime + 1000 * timeoutSeconds) {
                if (!readingThread.isAlive()) {
                    // Reading thread was able to read cube's state within a finite time.
                    break;
                }
            }
            // If the reading thread is still trying to read the cube's state, then there is probably a problem of starvation.
            if (readingThread.isAlive()) {
                fail();
            }

            // Writing threads are no longer useful, interrupt them.
            for (Thread t : threads) {
                t.interrupt();
            }
        }
    }


    /**
     * It tests for liveness of write operations.
     * <p>
     * If this test fails it means that there was no liveness of write operations, because:
     * the writing thread was not allowed to modify the cube's state while there was a heavy stream of read operations.
     */
    @ParameterizedTest
    @ValueSource(ints = {4, 8, 15, 32})
    @DisplayName("Tests if there is liveness of write operations.")
    void testLivenessOfWrites(int size) {
        int numberOfRuns = 10;
        int numberOfReadingThreads = 10;
        int timeoutSeconds = 5;
        Random random = new Random();

        for (int i = 0; i < numberOfRuns; i++) {
            Cube cube = cubeWithDummyWork(size, 0);

            // Create a collection of threads which perform infinite stream of reads.
            List<Thread> threads = new ArrayList<>();
            for (int j = 0; j < numberOfReadingThreads; j++) {
                threads.add(new Thread(() -> {
                    while (true) {
                        try {
                            cube.show();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }));
            }

            for (Thread t : threads) {
                t.start();
            }

            // Create a writing thread.
            Thread writingThread = new Thread(() -> {
                try {
                    cube.rotate(random.nextInt(Cube.getNumSides()), random.nextInt(size));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            writingThread.start();
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() < startTime + 1000 * timeoutSeconds) {
                if (!writingThread.isAlive()) {
                    // Writing thread was able to modify cube's state within a finite time.
                    break;
                }
            }
            // If the writing thread is still trying to modify the cube's state, then there is probably a problem of starvation.
            if (writingThread.isAlive()) {
                fail();
            }

            // Reading threads are no longer useful, interrupt them.
            for (Thread t : threads) {
                t.interrupt();
            }
        }
    }


    /**
     * It tests for correctness of interrupts' implementation.
     * <p>
     * If this test fails it means that interrupts are not working properly, either because:
     * target thread ignores interrupts,
     * or interrupted thread leaves the cube in an inconsistent state.
     */
    @ParameterizedTest
    @ValueSource(ints = {4, 8, 15, 32})
    @DisplayName("Tests if interrupts work properly.")
    void testInterrupts(int size) throws InterruptedException {
        int numberOfRuns = 100;
        int timeoutSeconds = 5;
        Random random = new Random();

        for (int i = 0; i < numberOfRuns; i++) {
            Cube cube = cubeWithDummyWork(size, 0);

            Runnable infiniteReading = () -> {
                while (true) {
                    try {
                        cube.show();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            };

            Runnable infiniteWriting = () -> {
                while (true) {
                    try {
                        cube.rotate(random.nextInt(Cube.getNumSides()), random.nextInt(size));
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            };

            // Create reading and writing threads that are working in an infinite loop.
            Thread firstReader = new Thread(infiniteReading);
            Thread secondReader = new Thread(infiniteReading);
            Thread firstWriter = new Thread(infiniteWriting);
            Thread secondWriter = new Thread(infiniteWriting);

            firstReader.start();
            secondReader.start();
            firstWriter.start();
            secondWriter.start();

            // Let the threads do some work.
            TimeUnit.MILLISECONDS.sleep(10);

            // Interrupt one reader and one writer.
            firstReader.interrupt();
            firstWriter.interrupt();

            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() < startTime + 1000 * timeoutSeconds) {
                if (!firstReader.isAlive() && !firstWriter.isAlive()) {
                    // Both the reader and the writer were successfully interrupted.
                    break;
                }
            }

            if (firstReader.isAlive() || firstWriter.isAlive()) {
                // The interruption did not work.
                System.err.println("Interruption timed out!");
                fail();
            }

            // Check if the cube's state is valid after interruption.
            if (!validateCubeByColorFrequency(cube)) {
                System.err.println("Interruption left the cube in an invalid state!");
                fail();
            }

            // Interrupt remaining threads.
            secondReader.interrupt();
            secondWriter.interrupt();
        }
    }


    /**
     * It tests for effectiveness of sequential and concurrent approach.
     * <p>
     * This test cannot fail, it is just for reference. Either of the approaches can yield better result, depending on
     * various factors.
     */
    @ParameterizedTest
    @ValueSource(ints = {64, 128})
    @DisplayName("Tests sequential and concurrent operations performance.")
    void testPerformance(int size) throws InterruptedException {
        int numberOfThreads = 32;
        int dummyWork = 1000000;
        int numberOfRotations = 100000;
        int numberOfShowings = 100000;

        Cube cube = cubeWithDummyWork(size, dummyWork);

        ExecutorService taskExecutorThreads = Executors.newFixedThreadPool(numberOfThreads);
        Random randomNumberGenerator = new Random();
        List<Future<?>> futures = new ArrayList<>();

        // Start of concurrent operations test.
        long start = System.currentTimeMillis();
        // Generate concurrent rotations.
        for (int i = 0; i < numberOfRotations; i++) {
            futures.add(taskExecutorThreads.submit(() -> {
                try {
                    cube.rotate(randomNumberGenerator.nextInt(Cube.getNumSides()), randomNumberGenerator.nextInt(size));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }));
        }

        // Generate concurrent readings.
        for (int i = 0; i < numberOfShowings; i++) {
            futures.add(taskExecutorThreads.submit(() -> {
                try {
                    cube.show();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }));
        }

        // Wait untill all futures are completed.
        returnWhenAllFuturesAreCompleted(futures);
        long concurrentTime = System.currentTimeMillis() - start;
        System.out.println("Concurrent: " + concurrentTime);

        // Start of sequential operations test.
        start = System.currentTimeMillis();
        for (int i = 0; i < numberOfRotations; i++) {
            cube.rotate(randomNumberGenerator.nextInt(Cube.getNumSides()), randomNumberGenerator.nextInt(size));
        }
        for (int i = 0; i < numberOfShowings; i++) {
            cube.show();
        }
        long sequentialTime = System.currentTimeMillis() - start;
        System.out.println("Sequential: " + sequentialTime);

        // Because of context switching, processes scheduling, number of CPU cores and other important factors, the
        // sequential approach might sometimes be faster (though on my computer the concurrent approach was faster).
        if (concurrentTime < sequentialTime) {
            System.out.println("Winner: concurrent");
        } else {
            System.out.println("Winner: sequential");
        }
    }

    /**
     * Checks if each color of the cube's squares appears the same number of times.
     *
     * @param cube Cube to be validated.
     * @return True iff each color of the cube's squares appears the same number of times.
     */
    boolean validateCubeByColorFrequency(Cube cube) throws InterruptedException {
        // Get cube's representation.
        String cubeSerialized = cube.show();
        int numberOfSides = Cube.getNumSides();

        // Count how many times each color appeared.
        int[] counter = new int[numberOfSides];
        for (int i = 0; i < cubeSerialized.length(); i++) {
            counter[cubeSerialized.charAt(i) - '0']++;
        }

        // Return false if there is any color that appears more or less frequently than others.
        int expected = cubeSerialized.length() / numberOfSides;
        for (int i = 0; i < numberOfSides; i++) {
            if (counter[i] != expected) return false;
        }
        return true;
    }


    /**
     * Helper function which returns as soon as all provided futures are completed.
     *
     * @param futures List of futures to be completed.
     */
    void returnWhenAllFuturesAreCompleted(List<Future<?>> futures) {
        int numberOfFutures = futures.size();
        int numberOfCompletedFutures = 0;
        boolean[] futureDone = new boolean[numberOfFutures];

        while (true) {
            for (int i = 0; i < numberOfFutures; i++) {
                // If another future was completed, then increase the counter.
                if (!futureDone[i] && futures.get(i).isDone()) {
                    futureDone[i] = true;
                    numberOfCompletedFutures++;
                }
                // If all futures were completed, then return.
                if (numberOfCompletedFutures == numberOfFutures) {
                    return;
                }
            }
        }
    }


    /**
     * Helper function which creates a cube with dummy work before and after rotation as well as before and after
     * checking cube's state.
     *
     * @param size Size of a cube to be created.
     * @param loopIterations Number of loop iterations.
     * @return A cube with described properties.
     */
    Cube cubeWithDummyWork(int size, int loopIterations) {
        // Create dummy loops which imitate work on the cube.
        BiConsumer<Integer, Integer> rotationWaiting = (integer1, integer2) -> {
            int dummy = 0;
            for (int i = 0; i < loopIterations; i++) {
                dummy++;
            }
        };

        Runnable showingWaiting = () -> {
            int dummy = 0;
            for (int i = 0; i < loopIterations; i++) {
                dummy++;
            }
        };

        return new Cube(size, rotationWaiting, rotationWaiting, showingWaiting, showingWaiting);
    }
}
