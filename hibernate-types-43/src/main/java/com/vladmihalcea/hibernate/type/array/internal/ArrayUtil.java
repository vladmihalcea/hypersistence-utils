package com.vladmihalcea.hibernate.type.array.internal;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * @author Vlad Mihalcea
 */
public class ArrayUtil {

    public static <T> T deepCopy(Object objectArray) {
        Class arrayClass = objectArray.getClass();

        if (boolean[].class.equals(arrayClass)) {
            boolean[] array = (boolean[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else if (byte[].class.equals(arrayClass)) {
            byte[] array = (byte[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else if (short[].class.equals(arrayClass)) {
            short[] array = (short[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else if (int[].class.equals(arrayClass)) {
            int[] array = (int[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else if (long[].class.equals(arrayClass)) {
            long[] array = (long[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else if (float[].class.equals(arrayClass)) {
            float[] array = (float[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else if (double[].class.equals(arrayClass)) {
            double[] array = (double[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else if (char[].class.equals(arrayClass)) {
            char[] array = (char[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        } else {
            Object[] array = (Object[]) objectArray;
            return (T) Arrays.copyOf(array, array.length);
        }
    }

    public static Object[] wrapArray(Object objectArray) {
        Class arrayClass = objectArray.getClass();

        if (boolean[].class.equals(arrayClass)) {
            boolean[] fromArray = (boolean[]) objectArray;
            Boolean[] array = new Boolean[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else if (byte[].class.equals(arrayClass)) {
            byte[] fromArray = (byte[]) objectArray;
            Byte[] array = new Byte[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else if (short[].class.equals(arrayClass)) {
            short[] fromArray = (short[]) objectArray;
            Short[] array = new Short[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else if (int[].class.equals(arrayClass)) {
            int[] fromArray = (int[]) objectArray;
            Integer[] array = new Integer[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else if (long[].class.equals(arrayClass)) {
            long[] fromArray = (long[]) objectArray;
            Long[] array = new Long[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else if (float[].class.equals(arrayClass)) {
            float[] fromArray = (float[]) objectArray;
            Float[] array = new Float[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else if (double[].class.equals(arrayClass)) {
            double[] fromArray = (double[]) objectArray;
            Double[] array = new Double[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else if (char[].class.equals(arrayClass)) {
            char[] fromArray = (char[]) objectArray;
            Character[] array = new Character[fromArray.length];
            for (int i = 0; i < fromArray.length; i++) {
                array[i] = fromArray[i];
            }
            return array;
        } else {
            return (Object[]) objectArray;
        }
    }

    public static <T> T unwrapArray(Object[] objectArray, Class<T> arrayClass) {

        if (boolean[].class.equals(arrayClass)) {
            boolean[] array = new boolean[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Boolean) objectArray[i] : Boolean.FALSE;
            }
            return (T) array;
        } else if (byte[].class.equals(arrayClass)) {
            byte[] array = new byte[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Byte) objectArray[i] : 0;
            }
            return (T) array;
        } else if (short[].class.equals(arrayClass)) {
            short[] array = new short[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Short) objectArray[i] : 0;
            }
            return (T) array;
        } else if (int[].class.equals(arrayClass)) {
            int[] array = new int[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Integer) objectArray[i] : 0;
            }
            return (T) array;
        } else if (long[].class.equals(arrayClass)) {
            long[] array = new long[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Long) objectArray[i] : 0L;
            }
            return (T) array;
        } else if (float[].class.equals(arrayClass)) {
            float[] array = new float[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Float) objectArray[i] : 0f;
            }
            return (T) array;
        } else if (double[].class.equals(arrayClass)) {
            double[] array = new double[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Double) objectArray[i] : 0d;
            }
            return (T) array;
        } else if (char[].class.equals(arrayClass)) {
            char[] array = new char[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                array[i] = objectArray[i] != null ? (Character) objectArray[i] : 0;
            }
            return (T) array;
        } else if (Enum[].class.isAssignableFrom(arrayClass)) {
          T array = arrayClass.cast(Array.newInstance(arrayClass.getComponentType(), objectArray.length));
          for (int i = 0; i < objectArray.length; i++) {
              Object objectValue = objectArray[i];
              if (objectValue != null) {
                  String stringValue = (objectValue instanceof String) ? (String) objectValue : String.valueOf(objectValue);
                  objectValue = Enum.valueOf((Class) arrayClass.getComponentType(), stringValue);
              }
              Array.set(array, i, objectValue);
          }
          return array;
        } else {
            return (T) objectArray;
        }
    }

    public static <T> T fromString(String string, Class<T> arrayClass) {
        String stringArray = string.replaceAll("[\\[\\]]", "");
        String[] tokens = stringArray.split(",");

        int length = tokens.length;

        if (boolean[].class.equals(arrayClass)) {
            boolean[] array = new boolean[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = Boolean.valueOf(tokens[i]);
            }
            return (T) array;
        } else if (byte[].class.equals(arrayClass)) {
            byte[] array = new byte[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = Byte.valueOf(tokens[i]);
            }
            return (T) array;
        } else if (short[].class.equals(arrayClass)) {
            short[] array = new short[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = Short.valueOf(tokens[i]);
            }
            return (T) array;
        } else if (int[].class.equals(arrayClass)) {
            int[] array = new int[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = Integer.valueOf(tokens[i]);
            }
            return (T) array;
        } else if (long[].class.equals(arrayClass)) {
            long[] array = new long[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = Long.valueOf(tokens[i]);
            }
            return (T) array;
        } else if (float[].class.equals(arrayClass)) {
            float[] array = new float[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = Float.valueOf(tokens[i]);
            }
            return (T) array;
        } else if (double[].class.equals(arrayClass)) {
            double[] array = new double[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = Double.valueOf(tokens[i]);
            }
            return (T) array;
        } else if (char[].class.equals(arrayClass)) {
            char[] array = new char[length];
            for (int i = 0; i < tokens.length; i++) {
                array[i] = tokens[i].length() > 0 ? tokens[i].charAt(0) : Character.MIN_VALUE;
            }
            return (T) array;
        } else {
            return (T) tokens;
        }
    }

    public static boolean isEquals(Object firstArray, Object secondArray) {
        if (firstArray.getClass() != secondArray.getClass()) {
            return false;
        }
        Class arrayClass = firstArray.getClass();

        if (boolean[].class.equals(arrayClass)) {
            return Arrays.equals((boolean[]) firstArray, (boolean[]) secondArray);
        } else if (byte[].class.equals(arrayClass)) {
            return Arrays.equals((byte[]) firstArray, (byte[]) secondArray);
        } else if (short[].class.equals(arrayClass)) {
            return Arrays.equals((short[]) firstArray, (short[]) secondArray);
        } else if (int[].class.equals(arrayClass)) {
            return Arrays.equals((int[]) firstArray, (int[]) secondArray);
        } else if (long[].class.equals(arrayClass)) {
            return Arrays.equals((long[]) firstArray, (long[]) secondArray);
        } else if (float[].class.equals(arrayClass)) {
            return Arrays.equals((float[]) firstArray, (float[]) secondArray);
        } else if (double[].class.equals(arrayClass)) {
            return Arrays.equals((double[]) firstArray, (double[]) secondArray);
        } else if (char[].class.equals(arrayClass)) {
            return Arrays.equals((char[]) firstArray, (char[]) secondArray);
        } else {
            return Arrays.equals((Object[]) firstArray, (Object[]) secondArray);
        }
    }

}
