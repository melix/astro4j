/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.math.matrix;

import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;

import java.util.Arrays;

/** Matrix operations on double precision arrays. */
public class DoubleMatrix {
    private final double[][] matrix;
    private final int rows;
    private final int cols;

    /**
     * Wraps the supplied double array as a matrix
     * @param m the matrix data
     * @return a double matrix
     */
    public static DoubleMatrix of(double[][] m) {
        int rows = m.length;
        int cols;
        if (rows == 0) {
            cols = 0;
        } else {
            cols = m[0].length;
        }
        return new DoubleMatrix(m, rows, cols);
    }

    private DoubleMatrix(double[][] m, int rows, int cols) {
        this.matrix = m;
        this.rows = rows;
        this.cols = cols;
    }

    /**
     * Returns the transposed matrix.
     * @return the transposed matrix
     */
    public DoubleMatrix transpose() {
        if (rows == 0) {
            return this;
        }
        double[][] result = new double[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[j][i] = matrix[i][j];
            }
        }
        return new DoubleMatrix(result, cols, rows);
    }

    /**
     * Computes the multiplication of this matrix with
     * the one supplied as a parameter
     * @param m2 the matrix to multiply with
     * @return the result of the multiplication
     */
    public DoubleMatrix mul(DoubleMatrix m2) {
        int m2Rows = m2.rows;
        int m2Cols = m2.cols;
        if (cols != m2Rows) {
            throw new IllegalArgumentException("The number of colums of this matrix is not equal to the number of rows of the other matrix so multiplication is undefined");
        }
        double[][] result = new double[rows][m2Cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < m2Cols; j++) {
                double sum = 0;
                for (int k = 0; k < cols; k++) {
                    sum += matrix[i][k] * m2.matrix[k][j];
                }
                result[i][j] = sum;
            }
        }
        return new DoubleMatrix(result, rows, m2Cols);
    }


    /**
     * Computes the inverse of a square matrix.
     *
     * @return the inverse of the input matrix
     */
    public DoubleMatrix inverse() {
        double[][] result = new double[rows][rows];
        double[][] augmented = new double[rows][2 * cols];

        // Copy the matrix and the identity matrix into the augmented matrix
        for (int i = 0; i < rows; i++) {
            System.arraycopy(matrix[i], 0, augmented[i], 0, rows);
            augmented[i][rows + i] = 1;
        }

        // Perform row operations to transform the augmented matrix into row echelon form
        for (int k = 0; k < rows; k++) {
            int max = k;
            for (int i = k + 1; i < rows; i++) {
                if (Math.abs(augmented[i][k]) > Math.abs(augmented[max][k])) {
                    max = i;
                }
            }
            double[] temp = augmented[k];
            augmented[k] = augmented[max];
            augmented[max] = temp;
            for (int i = k + 1; i < rows; i++) {
                double factor = augmented[i][k] / augmented[k][k];
                for (int j = k + 1; j < 2 * rows; j++) {
                    augmented[i][j] -= factor * augmented[k][j];
                }
                augmented[i][k] = 0;
            }
        }

        // Perform row operations to transform the augmented matrix into reduced row echelon form
        for (int k = rows - 1; k >= 0; k--) {
            for (int i = k - 1; i >= 0; i--) {
                double factor = augmented[i][k] / augmented[k][k];
                for (int j = k + 1; j < 2 * rows; j++) {
                    augmented[i][j] -= factor * augmented[k][j];
                }
                augmented[i][k] = 0;
            }
            double divisor = augmented[k][k];
            for (int j = k + 1; j < 2 * rows; j++) {
                augmented[k][j] /= divisor;
            }
            augmented[k][k] = 1;
        }

        // Extract the inverse matrix from the augmented matrix
        for (int i = 0; i < rows; i++) {
            System.arraycopy(augmented[i], rows, result[i], 0, rows);
        }

        return new DoubleMatrix(result, rows, cols);
    }

    /**
     * Adds another matrix to this one
     * @param m the other matrix
     * @return the result of the addition
     */
    public DoubleMatrix add(DoubleMatrix m) {
        double[][] m2 = m.matrix;
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = matrix[i][j] + m2[i][j];
            }
        }
        return new DoubleMatrix(result, rows, cols);
    }

    /**
     * Returns this matrix with all coefficients negated.
     * @return this matrix with all coefficients negated
     */
    public DoubleMatrix neg() {
        return mul(-1d);
    }

    /**
     * Multiplies this matrix with a scalar
     * @param scalar the multiplcation factor
     * @return this matrix multiplied by the scalar
     */
    public DoubleMatrix mul(double scalar) {
        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = matrix[i][j] * scalar;
            }
        }
        return new DoubleMatrix(result, rows, cols);
    }

    /**
     * Resoves the eigenvalues and eigenvectors of this matrix.
     * TODO: Implement without commons-math
     *
     * @return the eigen system
     */
    public EigenSystem solveEigenSystem() {
        var m = MatrixUtils.createRealMatrix(this.matrix);
        var eigen = new EigenDecomposition(m);
        double[] realEigenvalues = eigen.getRealEigenvalues();
        double[][] eigenvectors = eigen.getV().getData();
        return new EigenSystem(realEigenvalues, eigenvectors);
    }


    /**
     * Used for debugging computations using SageMath.
     */
    private static String toSageMath(String name, double[][] matrix) {
        var sb = new StringBuilder();
        sb.append(name).append("=matrix(");
        sb.append("[");
        for (int i = 0; i < matrix.length; i++) {
            sb.append("[");
            for (int j = 0; j < matrix[i].length; j++) {
                sb.append(matrix[i][j]);
                if (j < matrix[i].length - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            if (i < matrix.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("])");
        return sb.toString();
    }

    /**
     * Returns the backing array of this matrix.
     * @return the backing array of this matrix
     */
    public double[][] asArray() {
        return matrix;
    }

    /**
     * Container for eigenvalues and eigenvectors.
     * @param values the eigenvalues
     * @param vectors the eigenvectors
     */
    public record EigenSystem(double[] values, double[][] vectors) {
        @Override
        public String toString() {
            return "EigenSystem{" +
                   "values=" + Arrays.toString(values) +
                   ", vectors=" + Arrays.toString(vectors) +
                   '}';
        }
    }
}
