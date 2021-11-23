package concurrentcube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;


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

    @Test
    @DisplayName("")
    void testConcurrentThreadsSameRotation() throws InterruptedException {
        AtomicInteger a = new AtomicInteger(0);

        Cube cube = new Cube(2, (x, y) -> { a.addAndGet(1);
        }, (x, y) -> { a.addAndGet(1);
        }, () -> {
        }, () -> {
        });

        // get initial cube's state
        String cubeBeforeCyclicRotations = cube.show();

        ExecutorService taskExecutorThreads = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 4; i++) {
            taskExecutorThreads.submit(() -> {
                try {
                    cube.rotate(5, 0);
                    cube.rotate(0, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        taskExecutorThreads.awaitTermination(4, TimeUnit.SECONDS);
        System.out.println(a);
        TimeUnit.SECONDS.sleep(2);
        String cubeAfterCyclicRotations = cube.show();
        assertEquals(cubeBeforeCyclicRotations, cubeAfterCyclicRotations);
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
