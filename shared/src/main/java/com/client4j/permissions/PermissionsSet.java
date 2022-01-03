/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions;

import ca.weblite.jdeploy.app.AppInfo;

import java.util.*;

/**
 *
 * @author shannah
 */
public class PermissionsSet extends Observable implements Set<AppInfo.Permission> {
    
    private class Key {
        private final AppInfo.Permission perm;
        
        Key(AppInfo.Permission perm) {
            this.perm = perm.copy();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Key) {
                Key k = (Key)obj;
                return k.perm.toString().equals(perm.toString());
            }
            return false;  
        }

        @Override
        public int hashCode() {
            return perm.toString().hashCode();
        }
        
        
    }
    
    private Map<Key,AppInfo.Permission> m = new HashMap<>();
    @Override
    public int size() {
        return m.size();
    }

    @Override
    public boolean isEmpty() {
        return m.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof AppInfo.Permission) {
            return m.containsKey(new Key((AppInfo.Permission)o));
        }
        return false;
    }

    @Override
    public Iterator<AppInfo.Permission> iterator() {
        return m.values().iterator();
    }

    @Override
    public Object[] toArray() {
        return m.values().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return m.values().toArray(a);
    }

    @Override
    public boolean add(AppInfo.Permission e) {
        if (!contains(e)) {
            m.put(new Key(e), e.copy());
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        if (contains(o)) {
            m.remove(new Key((AppInfo.Permission)o));
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends AppInfo.Permission> c) {
        boolean changed = false;
        for (AppInfo.Permission p : c) {
            if (!contains(p)) {
                add(p);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }
    
    
    public boolean addAll(Iterable<? extends AppInfo.Permission> c) {
        if (c == null) {
            return false;
        }
        boolean changed = false;
        for (AppInfo.Permission p : c) {
            if (!contains(p)) {
                add(p);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        Iterator<Key> keys = m.keySet().iterator();
        HashSet<Key> cKeys = new HashSet<>();
        for (Object o : c) {
            cKeys.add(new Key((AppInfo.Permission)o));
        }
        while (keys.hasNext()) {
            Key nex = keys.next();
            if (!cKeys.contains(nex)) {
                m.remove(nex);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = true;
        for (Object o : c) {
            changed = remove((AppInfo.Permission)o) || changed;
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    @Override
    public void clear() {
        if (!isEmpty()) {
            m.clear();
            setChanged();
        }
    }
    
    
    public static PermissionsSet unionOf(PermissionsSet... sets) {
        PermissionsSet out = new PermissionsSet();
        for (PermissionsSet s : sets) {
            out.addAll(s);
        }
        return out;
    }
    
    public boolean equals(Object o) {
        if (o instanceof PermissionsSet) {
            return equalsImpl((PermissionsSet)o);
        }
        return super.equals(o);
    }
    
    private boolean equalsImpl(PermissionsSet s) {
        if (s.size() != size()) {
            return false;
        }
        return unionOf(s, this).size() == size();
    }
    
    public PermissionsSet copy() {
        PermissionsSet out = new PermissionsSet();
        out.addAll(this);
        return out;
    }
}


