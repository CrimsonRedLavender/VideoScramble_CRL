package com.example.videoscramble;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

public final class LineScrambler {
    private LineScrambler() {
    }

    public static Mat scramble(Mat input, ScrambleKey key) {
        return transform(input, key, true);
    }

    public static Mat unscramble(Mat input, ScrambleKey key) {
        return transform(input, key, false);
    }

    public static List<Integer> blockSizes(int height) {
        List<Integer> blocks = new ArrayList<>();
        int remaining = height;
        while (remaining > 0) {
            int block = highestPowerOfTwoLessOrEqual(remaining);
            blocks.add(block);
            remaining -= block;
        }
        return blocks;
    }

    private static Mat transform(Mat input, ScrambleKey key, boolean encrypt) {
        Mat output = input.clone();
        int startRow = 0;
        int remaining = input.rows();

        while (remaining > 0) {
            int blockSize = highestPowerOfTwoLessOrEqual(remaining);
            if (blockSize == 1) {
                input.row(startRow).copyTo(output.row(startRow));
                startRow += 1;
                remaining -= 1;
                continue;
            }

            transformBlock(input, output, startRow, blockSize, key, encrypt);
            startRow += blockSize;
            remaining -= blockSize;
        }

        return output;
    }

    private static void transformBlock(Mat input, Mat output, int rowOffset, int size, ScrambleKey key, boolean encrypt) {
        int a = key.oddMultiplier() % size;
        int inverseA = modInversePowerOfTwo(a, size);

        for (int localRow = 0; localRow < size; localRow++) {
            int destination;
            if (encrypt) {
                destination = (key.offset() + a * localRow) % size;
                input.row(rowOffset + localRow).copyTo(output.row(rowOffset + destination));
            } else {
                int source = ((localRow - key.offset()) % size + size) % size;
                source = (inverseA * source) % size;
                input.row(rowOffset + localRow).copyTo(output.row(rowOffset + source));
            }
        }
    }

    private static int highestPowerOfTwoLessOrEqual(int value) {
        int power = 1;
        while ((power << 1) > 0 && (power << 1) <= value) {
            power <<= 1;
        }
        return power;
    }

    private static int modInversePowerOfTwo(int a, int modulus) {
        for (int x = 1; x < modulus; x++) {
            if ((a * x) % modulus == 1) {
                return x;
            }
        }
        throw new IllegalArgumentException("Aucun inverse modulaire pour a=" + a + " modulo " + modulus);
    }
}
