package com.sfm_optimizer.transfer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvenSplitMathTest {
    @Test void exactDivision() {
        assertArrayEquals(new long[]{4, 3, 3}, EvenSplitMath.evenSplit(10, new long[]{100, 100, 100}));
    }
    @Test void respectsCapacityCaps() {
        assertArrayEquals(new long[]{3, 3, 3}, EvenSplitMath.evenSplit(10, new long[]{3, 3, 3}));
    }
    @Test void remainderGoesToFront() {
        assertArrayEquals(new long[]{3, 2}, EvenSplitMath.evenSplit(5, new long[]{10, 10}));
    }
    @Test void zeroTotal() {
        assertArrayEquals(new long[]{0, 0}, EvenSplitMath.evenSplit(0, new long[]{5, 5}));
    }
    @Test void emptyDestinations() {
        assertArrayEquals(new long[]{}, EvenSplitMath.evenSplit(7, new long[]{}));
    }
    @Test void unevenCapsWaterFill() {
        assertArrayEquals(new long[]{2, 2, 1}, EvenSplitMath.evenSplit(5, new long[]{2, 100, 100}));
    }
}
