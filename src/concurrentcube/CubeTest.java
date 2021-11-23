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

}
