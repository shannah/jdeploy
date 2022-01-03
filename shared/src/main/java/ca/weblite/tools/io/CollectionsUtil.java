/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author shannah
 */
public class CollectionsUtil {
    public static void addAll(Collection target, Iterable source) {
        for (Object o : source) {
            target.add(o);
        }
    }
    
    public static <T> List<T> asList(Iterable<T> source) {
        if (source instanceof List) {
            return (List<T>)source;
        }
        ArrayList<T> out = new ArrayList<>();
        addAll(out, source);
        return out;
    }
}
