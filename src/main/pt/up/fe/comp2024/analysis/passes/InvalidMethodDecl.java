package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

public class InvalidMethodDecl extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        var children = methodDecl.getChildren();

        // Check varargs

        if (!methodDecl.get("name").equals("main")) {
            var returnTypeNode = children.get(0);
            if (Kind.check(returnTypeNode, Kind.VARARGS_TYPE)) {
                if (returnTypeNode.getObject("isVarargs", Boolean.class)) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(returnTypeNode),
                            NodeUtils.getColumn(returnTypeNode),
                            "Return type cannot be varargs.",
                            null
                    ));
                    return null;
                }
            }
        }

        var params = table.getParameters(methodDecl.get("name"));
        for (int i = 0; i < params.size() - 1; i++) {
            var param = params.get(i);
            if (param.getType().getObject("isVarargs", Boolean.class) && i != params.size() - 1) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        "Varargs must be the last parameter of a method.",
                        null
                ));
            }
        }

        // Check return type
        if (!children.isEmpty() && !methodDecl.get("name").equals("main")) {
            var returnExpr = methodDecl.getChild(methodDecl.getNumChildren() - 1);

            if (returnExpr == null)
                return null;

            var returnType = table.getReturnType(methodDecl.get("name"));
            var returnExprType = TypeUtils.getExprType(returnExpr, table);

            if (!returnType.equals(returnExprType)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnExpr),
                        NodeUtils.getColumn(returnExpr),
                        "Return type does not match method declaration.",
                        null
                ));
            }
        }

        return null;
    }
}
