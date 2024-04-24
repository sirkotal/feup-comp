package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

public class InvalidVars extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.IMPORT_DECL, this::visitImportDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.PARAM, this::visitParam);
    }

    private Void visitVarDecl(JmmNode varDeclNode, SymbolTable table) {
        if (Kind.check(varDeclNode.getParent(), Kind.CLASS_DECL)) {
            var fields = table.getFields();

            // Check if the field name is duplicated
            if (fields.stream().filter(field -> field.getName().equals(varDeclNode.get("name"))).count() > 1) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDeclNode),
                        NodeUtils.getColumn(varDeclNode),
                        "Duplicated field name: " + varDeclNode.get("name"),
                        null
                ));
            }

            // Check if the field is not varargs
            if (varDeclNode.getJmmChild(0).getObject("isVarargs", Boolean.class)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDeclNode),
                        NodeUtils.getColumn(varDeclNode),
                        "Field cannot be varargs: " + varDeclNode.get("name"),
                        null
                ));
            }

            return null;
        }

        if (Kind.check(varDeclNode.getParent(), Kind.METHOD_DECL)) {
            var methodName = varDeclNode.getParent().get("name");
            var locals = table.getLocalVariables(methodName);

            // Check if the local variable name is duplicated
            if (locals.stream().filter(localVar -> localVar.getName().equals(varDeclNode.get("name"))).count() > 1) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDeclNode),
                        NodeUtils.getColumn(varDeclNode),
                        "Duplicated local variable name: " + varDeclNode.get("name"),
                        null
                ));
            }

            // Check if the local variable is not varargs
            if (varDeclNode.getJmmChild(0).getObject("isVarargs", Boolean.class)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDeclNode),
                        NodeUtils.getColumn(varDeclNode),
                        "Local variable cannot be varargs: " + varDeclNode.get("name"),
                        null
                ));
            }

            return null;
        }

        return null;
    }

    private Void visitImportDecl(JmmNode importDeclNode, SymbolTable table) {
        var importStr = String.join(
                ".",
                importDeclNode.getObjectAsList("value").stream().map(Object::toString).toList()
        );

        // Check if the import is duplicated
        if (table.getImports().stream().filter(importStr::equals).count() > 1) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(importDeclNode),
                    NodeUtils.getColumn(importDeclNode),
                    "Duplicated import: " + importStr,
                    null
            ));
        }

        // Check for duplicated classes imported (last in "().().CLASS")
        var tmp = importStr.split("\\.");
        String importClass = tmp[tmp.length - 1];
        var checkClassDup = table.getImports().stream().map(i -> {
            var t = i.split("\\.");
            return t[t.length - 1];
        }).filter(importClass::equals).count() > 1;

        if (checkClassDup) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(importDeclNode),
                    NodeUtils.getColumn(importDeclNode),
                    "Duplicated class imported: " + importStr,
                    null
            ));
        }

        return null;
    }

    private Void visitMethodDecl(JmmNode methodDeclNode, SymbolTable table) {
        // Check if the method name is duplicated
        if (table.getMethods().stream().filter(method -> method.equals(methodDeclNode.get("name"))).count() > 1) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDeclNode),
                    NodeUtils.getColumn(methodDeclNode),
                    "Duplicated method name: " + methodDeclNode.get("name"),
                    null
            ));
        }

        return null;
    }

    private Void visitParam(JmmNode paramNode, SymbolTable table) {
        if (Kind.check(paramNode.getParent(), Kind.METHOD_DECL)) {
            var methodName = paramNode.getParent().get("name");
            var params = table.getParameters(methodName);

            // Check if the parameter name is duplicated
            if (params.stream().filter(param -> param.getName().equals(paramNode.get("name"))).count() > 1) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(paramNode),
                        NodeUtils.getColumn(paramNode),
                        "Duplicated parameter name: " + paramNode.get("name"),
                        null
                ));
            }

            return null;
        }

        return null;
    }
}
