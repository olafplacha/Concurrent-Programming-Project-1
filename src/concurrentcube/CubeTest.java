package concurrentcube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class CubeTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 100, 201, 1024})
    @DisplayName("Ensures that sequential cyclic rotations does not change cube's state")
    void testSequentialCycleRotation(int size) throws InterruptedException {

        Cube cube = new Cube(size, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});

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

        // get cube's state after many rotations
        String cubeAfterCyclicRotations = cube.show();
        assertEquals(cubeBeforeCyclicRotations, cubeAfterCyclicRotations);
    }

}
