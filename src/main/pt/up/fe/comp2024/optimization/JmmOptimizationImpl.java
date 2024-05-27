package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast2jasmin.AstToJasmin;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;
import java.util.*;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());
        var ollirCode = visitor.visit(semanticsResult.getRootNode());
        System.out.println("The OLLIR code is as follows:");
        System.out.println(ollirCode);
        System.out.println("<----------------------------->");
        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        // TODO: To implement for CP3
        return JmmOptimization.super.optimize(semanticsResult);
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        int max_registers = Integer.parseInt(ollirResult.getConfig().getOrDefault("registerAllocation", "-1"));
        RegisterAllocator optimizer = new RegisterAllocator(ollirResult, max_registers);
        optimizer.allocateReg();

        return ollirResult;
    }
}
