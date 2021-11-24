package concurrentcube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class CubeTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 100, 201, 512})
    @DisplayName("Ensures that sequential cyclic rotations does not change cube's state")
    void testSequentialCycleRotations(int size) throws InterruptedException {

        Cube cube = new Cube(size, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        // get initial cube's state
        String cubeBeforeCyclicRotations = cube.show();

        Random rand = new Random();
        int numCyclicRotations = 1000;
        for (int i = 0; i < numCyclicRotations; i++) {
            // pick random side and layer for the cyclic rotation
            int side = rand.nextInt(6);
            int layer = rand.nextInt(size);
            for (int j = 0; j < 4; j++) {
                cube.rotate(side, layer);
            }
        }

        // get cube's state after many cyclic rotations
        String cubeAfterCyclicRotations = cube.show();
        assertEquals(cubeBeforeCyclicRotations, cubeAfterCyclicRotations);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 100, 201})
    @DisplayName("Check if sequential rotations work properly on one magic rotations sequence")
    void testSequentialSequenceOfRotations(int size) throws InterruptedException {
        Cube cube = new Cube(size, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        // get initial cube's state
        String cubeBeforeCyclicRotations = cube.show();

        // regardless of the size of the cube, the following sequence of moves, repeated 1260 times
        // must yield initial cube's configuration
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

    boolean sameNumberOfDigits(String s) {
        int[] counter = new int[6];
        for (int i = 0; i < s.length(); i++) {
            counter[s.charAt(i)-'0']++;
        }
        int expected = s.length() / 6;
        for (int i = 0; i < 6; i++) {
            if (counter[i] != expected) return false;
        }
        return true;
    }

    @RepeatedTest(100)
    @DisplayName("Test concurrent cyclic rotations.")
    void testConcurrentThreadsSameRotation() throws InterruptedException, ExecutionException {
        AtomicInteger a = new AtomicInteger(0);

        Cube cube = new Cube(10, (x, y) -> { a.addAndGet(1);
        }, (x, y) -> { a.addAndGet(1);
        }, () -> {
        }, () -> {
        });

        // get initial cube's state
        String cubeBeforeCyclicRotations = cube.show();

        List<Future<?>> futures = new ArrayList<>();
        List<Boolean> done = new ArrayList<>();
        List<Future<String>> cubeStates = new ArrayList<>();

        int k = 10000;
        ExecutorService taskExecutorThreads = Executors.newFixedThreadPool(32);

        for (int i = 0; i < k; i++) {
            futures.add(taskExecutorThreads.submit(() -> {
                try {
                    cube.rotate(5, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }));
            done.add(false);
        }

        for (int i = 0; i < k; i++) {
            cubeStates.add(taskExecutorThreads.submit(() -> {
                try {
                    return cube.show();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return "";
                }
            }));
        }

        for (int i = 0; i < k; i++) {
            futures.add(taskExecutorThreads.submit(() -> {
                try {
                    cube.rotate(5, 1);
                    cube.rotate(4, 9);
                    cube.rotate(2, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }));
            done.add(false);
        }

        int doneCounter = 0;
        int currIdx = 0;
        while (true) {
            if (!done.get(currIdx) && futures.get(currIdx).isDone()) {
                try {
                    futures.get(currIdx).get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                done.set(currIdx, true);
                doneCounter++;
            }
            if (doneCounter == done.size()) break;
            currIdx = (currIdx + 1) % done.size();
        }

//        for (int i = 0; i < cubeStates.size(); i++) {
//            String state = cubeStates.get(i).get();
//            assertTrue(sameNumberOfDigits(state));
//        }
//
//        String cubeAfterCyclicRotations = cube.show();
//        assertEquals(cubeBeforeCyclicRotations, cubeAfterCyclicRotations);
    }

    void showRep(String rep, int s) {
        int c = 0;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < s; j++) {
                String a = "";
                for (int k = 0; k < s; k++) {
                    a += rep.charAt(c);
                    c++;
                }
                System.out.println(a);
            }
            System.out.println();
        }
    }

    @Test
    void test() throws InterruptedException {
        Cube cube = new Cube(3, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        cube.rotate(3, 0);
        String rep = cube.show();
        showRep(rep, 3);
    }

    /**
     * Helper function which returns as soon as all provided futures are completed
     * @param futures list of futures to be completed
     */
    void returnWhenAllFuturesAreCompleted(List<Future<?>> futures) {
        int numberOfFutures = futures.size();
        int numberOfCompletedFutures = 0;
        boolean[] futureDone = new boolean[numberOfFutures];

        while (true) {
            for (int i = 0; i < numberOfFutures; i++) {
                // if another future was completed, increase the counter
                if (!futureDone[i] && futures.get(i).isDone()) {
                    futureDone[i] = true;
                    numberOfCompletedFutures++;
                }
                // if all futures were completed, return
                if (numberOfCompletedFutures == numberOfFutures) {
                    return;
                }
            }
        }
    }

    /**
     * Helper function which creates a cube with dummy work before and after rotation as well as before and after
     * checking cube's state - it helps to see the superiority of concurrent approach over sequential one
     * @param size size of cube to be created
     * @param loopIterations number of loop iterations
     * @return a cube with described properties
     */
    Cube createSimpleCubeWithDummyWork(int size, int loopIterations) {

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

    /**
     * Helper function checking if the number of squares is equal for each color of the cube
     */
    boolean checkIfEqualNumberOfSquaresOfEachColor(Cube cube) throws InterruptedException {
        int size = cube.getSize();
        int[] counterForEachColor = new int[size];

        String cubeState = cube.show();
        for (int i = 0; i < cubeState.length(); i++) {
            counterForEachColor[cubeState.charAt(i) - '0']++;
        }

        for (int i = 0; i < 6; i++) {
            if (counterForEachColor[i] != size * size) return false;
        }
        return true;
    }


    @Test
    @RepeatedTest(10)
    @DisplayName("Tests sequential vs concurrent rotations and reads performance.")
    void testPerformance() throws InterruptedException {

        final int size = 32;
        final int dummyWork = 1000000000;
        int numberOfRotations = 100000;
        int numberOfShowings = 100000;

        Cube cube = createSimpleCubeWithDummyWork(size, dummyWork);
        ExecutorService taskExecutorThreads = Executors.newFixedThreadPool(8);
        Random randomNumberGenerator = new Random();
        List<Future<?>> futures = new ArrayList<>();

        // start of concurrent operations test
        long start = System.currentTimeMillis();
        // generate concurrent rotations
        for (int i = 0; i < numberOfRotations; i++) {
            futures.add(taskExecutorThreads.submit(() -> {
                try {
                    cube.rotate(randomNumberGenerator.nextInt(6), randomNumberGenerator.nextInt(size));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }));
        }

        // generate consurrent readings
        for (int i = 0; i < numberOfShowings; i++) {
            futures.add(taskExecutorThreads.submit(() -> {
                try {
                    cube.show();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }));
        }

        // wait untill all futures are completed
        returnWhenAllFuturesAreCompleted(futures);
        long concurrentTime = System.currentTimeMillis() - start;
        System.out.println("Concurrent: " + concurrentTime);

        // assert that concurrency errors did not make any color disappear
        assertTrue(checkIfEqualNumberOfSquaresOfEachColor(cube));

        // start of sequential operations test
        start = System.currentTimeMillis();
        for (int i = 0; i < numberOfRotations; i++) {
            cube.rotate(randomNumberGenerator.nextInt(6), randomNumberGenerator.nextInt(size));
        }
        for (int i = 0; i < numberOfShowings; i++) {
            cube.show();
        }
        long sequentialTime = System.currentTimeMillis() - start;
        System.out.println("Sequential: " + sequentialTime);

        // because of context switching, processes scheduling and other overheards of concurrent programming, the
        // sequantial approach might sometimes be faster (on my computer the concurrent approach was faster!)
        if (concurrentTime < sequentialTime) {
            System.out.println("Winner: concurrent");
        }
        else {
            System.out.println("Winner: sequential");
        }
    }



}
