package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.ClassUnit;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import java.util.Collections;

public class RegisterAllocator {
    private int regs;
    private ClassUnit unit;

    public RegisterAllocator(OllirResult ollirResult, int n) {
        this.unit = ollirResult.getOllirClass();
        this.regs = n;
    }

    public void allocateReg() {
        this.unit.buildCFGs();
        this.unit.buildVarTables();

        for (var m : this.unit.getMethods()) {
            // TODO if there is time
            DataAnalysis.runAnalysis(m);
        }
    }
}
