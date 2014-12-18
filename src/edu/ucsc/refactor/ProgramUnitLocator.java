package edu.ucsc.refactor;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
public class ProgramUnitLocator implements UnitLocator {
    private final Context  context;
    private final Record   record;

    /**
     * Constructs a new {@code ProgramUnitLocator} with a {@code Context} as
     * a value.
     *
     * @param context THe Java {@code Context}.
     */
    public ProgramUnitLocator(Context context){
        this.context    = context;
        this.record     = new Record();
    }

    @Override public List<NamedLocation> locate(ProgramUnit unit) {
        track(unit.getName(), unit);

        return unit.getLocations(context);
    }


    Context getContext(){
        return context;
    }

    private void track(String key, ProgramUnit hint){
        record.key  = Preconditions.checkNotNull(key);
        record.hint = Preconditions.checkNotNull(hint);
    }

    @Override public String toString() {
        final String      target = record.key;
        final ProgramUnit hint   = record.hint;
        return "Search for " + target + " " + hint + " in " + getContext();
    }


    static class Record {
        String      key;
        ProgramUnit hint;
    }
}
