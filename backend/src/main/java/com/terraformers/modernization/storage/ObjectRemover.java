package com.terraformers.modernization.storage;

public interface ObjectRemover {

    /** Removes the reference if present; implementations must also succeed when it is already absent. */
    void remove(ObjectReference reference);
}
