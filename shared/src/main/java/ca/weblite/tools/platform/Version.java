/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author shannah
 */
public class Version implements Comparable<Version> {
    
    private Version(int[] parts) {
        this.parts = parts;
    }
    
    
    public static Version parse(String version) {
        List<Integer> out = new ArrayList<>();
        version = version.trim();
        StringBuilder sb = new StringBuilder();
        
        int len = version.length();
        for (int i=0; i<len; i++) {
            char c = version.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            } else {
                if (sb.length() == 0) {
                    out.add(0);
                } else {
                    out.add(Integer.parseInt(sb.toString()));
                }
                sb.setLength(0);
                if (c != '.') {
                    break;
                }
            }
        }
        if (sb.length() > 0) {
            out.add(Integer.parseInt(sb.toString()));
            sb.setLength(0);
        }
        if (out.size() < 2) {
            out.add(0);
        }
        
        return new Version(out.stream().mapToInt(j->j).toArray());
    }
    
    
    
    private final int[] parts;
    
    public Version getOptimisticMatchUpperBound() {
        
        Version bound = new Version(new int[parts.length-1]);
        System.arraycopy(parts, 0, bound.parts, 0, parts.length-1);
        bound.parts[bound.parts.length-1]++;
        return bound;
    }
    
    /**
     * Checks if this version matches the given string expression.  
     * @param expression E.g. >= 0.1.0
     * @return 
     */
    public boolean matches(String expression) {
        if (expression == null) {
            return true;
        }
        expression = expression.trim();
        if (expression.isEmpty()) {
            return true;
        }
        if (Character.isDigit(expression.charAt(0))) {
            return equals(parse(expression));
        }
        if (expression.startsWith("<=")) {
            String vStr = expression.substring(2);
            Version v = parse(vStr);
            return compareTo(v) <= 0;
        }
        if (expression.startsWith(">=")) {
            return compareTo(parse(expression.substring(2))) >= 0;
        }
        if (expression.startsWith(">")) {
            return compareTo(parse(expression.substring(1))) > 0;
        }
        if (expression.startsWith("<")) {
            return compareTo(parse(expression.substring(1))) < 0;
        }
        if (expression.startsWith("~>")) {
            Version minVersion = parse(expression.substring(2));
            Version upperBound = parse(expression.substring(2)).getOptimisticMatchUpperBound();
            return compareTo(minVersion) >= 0 && compareTo(upperBound) < 0;
        }
        throw new IllegalArgumentException("The expression "+expression+" is not a valid version match expression.  Expression must begin with either a digit, or one of the following comparators: >, <, <=, >=, ~>");
        
    }

    @Override
    public int compareTo(Version o) {
        int minLen = Math.min(parts.length, o.parts.length);
        for (int i=0; i<minLen; i++) {
            if (parts[i] < o.parts[i]) {
                return -1;
            } else if (parts[i] > o.parts[i]) {
                return 1;
            }
        }
        return parts.length > o.parts.length ? 1 : parts.length < o.parts.length ? -1 : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Version) {
            return Arrays.equals(parts, ((Version)obj).parts);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + Arrays.hashCode(this.parts);
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int len = parts.length;
        for (int i=0; i<len; i++) {
            if (i>0) {
                sb.append(".");
            }
            sb.append(parts[i]);
        }
        
        return sb.toString();
    }
    
    
    
}
