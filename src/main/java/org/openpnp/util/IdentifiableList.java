package org.openpnp.util;

import java.util.ArrayList;

import org.openpnp.model.Identifiable;

/**
 * A List specifically for storing implementations of Identifiable. This class adds a get(String)
 * method for getting the Identifiable object with the specified id from the list. Currently
 * performs a simple search through the list but is intended to be indexed eventually.
 * 
 * TODO: Perform indexing on insert and remove so that get(String) can perform better. Consider what
 * happens if an id changes out from under us.
 * 
 * @param <E>
 */
public class IdentifiableList<E extends Identifiable> extends ArrayList<E> {
    private static final long serialVersionUID = -2350184908321182804L;
    
    public IdentifiableList() {
        super();
    }
    
    public IdentifiableList(IdentifiableList<E> identifiableList) {
        super(identifiableList);
    }
    
    public String createId(String prefix) {
        //Only software people start counting at zero! Everyone else starts counting at one.
        for (int i = 1; ; i++) {
            if (get(prefix + i) == null) {
                return prefix + i;
            }
        }
    }

    public E get(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (E e : this) {
            if (e.getId().equals(id)) {
                return e;
            }
        }
        return null;
    }
}
