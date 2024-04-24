package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.METHOD_EXPR;

public class InvalidAssignments extends AnalysisVisitor {

        @Override
        public void buildVisitor() {
            addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
            addVisit(Kind.ASSIGN_ARRAY_STMT, this::visitAssignArrayStmt);
        }

        private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
            var varName = assignStmt.get("name");
            var expr = assignStmt.getJmmChild(0);

            validateAssignExpression(assignStmt.getAncestor(Kind.METHOD_DECL), varName, expr, table, false);
            return null;
        }

        private Void visitAssignArrayStmt(JmmNode assignArrayStmt, SymbolTable table) {
            var varName = assignArrayStmt.get("name");
            var idxExpr = assignArrayStmt.getJmmChild(0);
            var assignExpr = assignArrayStmt.getJmmChild(1);

            var idxType = TypeUtils.getExprType(idxExpr, table);
            if (!idxType.getName().equals(TypeUtils.getIntTypeName())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(idxExpr),
                        NodeUtils.getColumn(idxExpr),
                        "Invalid index type for array access.",
                        null
                ));

                return null;
            }

            validateAssignExpression(assignArrayStmt.getAncestor(Kind.METHOD_DECL), varName, assignExpr, table, true);
            return null;
        }

        private void validateAssignExpression(Optional<JmmNode> ancestorMethod, String varName, JmmNode expr, SymbolTable table, boolean arrayAccess) {
            ancestorMethod.ifPresentOrElse(
                    method -> {
                        var methodName = method.get("name");

                        table.getLocalVariables(methodName).stream()
                                .filter(var -> var.getName().equals(varName))
                                .findFirst()
                                .ifPresentOrElse(
                                        localVar -> typeChecking(expr, localVar, table, arrayAccess),
                                        () -> table.getParameters(methodName).stream()
                                                .filter(p -> p.getName().equals(varName))
                                                .findFirst().ifPresent(param -> typeChecking(expr, param, table, arrayAccess))
                                );
                    },
                    () -> table.getFields().stream()
                            .filter(f -> f.getName().equals(varName))
                            .findFirst().ifPresent(field -> typeChecking(expr, field, table, arrayAccess))
            );
        }

        private void typeChecking(JmmNode expr, Symbol symbol, SymbolTable table, boolean arrayAccess) {
            var symbolType = symbol.getType();

            if (symbolType.isArray()) {
                if (!Kind.check(expr, Kind.ARRAY_LITERAL, Kind.NEW_ARRAY)) {
                    if (arrayAccess) {
                        var exprType = TypeUtils.getExprType(expr, table);

                        if (!exprType.getName().equals(symbolType.getName())) {
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(expr),
                                    NodeUtils.getColumn(expr),
                                    "Invalid assignment to array variable. (Types are not compatible)",
                                    null
                            ));
                        }
                    } else {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(expr),
                                NodeUtils.getColumn(expr),
                                "Invalid assignment to array variable.",
                                null
                        ));
                    }
                } else {
                    // check if all elements have the same type
                    try {
                        var exprType = TypeUtils.getArrayLiteralType(expr, table);

                        if (!symbolType.getName().equals(exprType.getName())) {
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(expr),
                                    NodeUtils.getColumn(expr),
                                    "Invalid assignment to array variable. (Types are not compatible)",
                                    null
                            ));
                        }
                    } catch (Exception e) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(expr),
                                NodeUtils.getColumn(expr),
                                "Invalid assignment to array variable. (Array elements have different types)",
                                null
                        ));
                    }
                }
            } else if (Kind.check(expr, Kind.THIS_EXPR)) {
                expr.getAncestor(Kind.METHOD_DECL).ifPresentOrElse(
                        methodDecl -> {
                            if (methodDecl.get("name").equals("main")) {
                                addReport(Report.newError(
                                        Stage.SEMANTIC,
                                        NodeUtils.getLine(expr),
                                        NodeUtils.getColumn(expr),
                                        "Cannot use `this` expression in static methods.",
                                        null
                                ));
                            }
                        },
                        () -> expr.getAncestor(Kind.CLASS_DECL).ifPresentOrElse(
                                classDecl -> {
                                    var className = classDecl.get("name");

                                    if (!className.equals(symbolType.getName())) {
                                        addReport(Report.newError(
                                                Stage.SEMANTIC,
                                                NodeUtils.getLine(expr),
                                                NodeUtils.getColumn(expr),
                                                "Variable isn't the same type as the class.",
                                                null
                                        ));
                                    }
                                },
                                () -> addReport(Report.newError(
                                        Stage.SEMANTIC,
                                        NodeUtils.getLine(expr),
                                        NodeUtils.getColumn(expr),
                                        "`this` expression is not inside a class declaration.",
                                        null
                                ))
                        )
                );
            } else {
                if (Kind.check(expr, Kind.ARRAY_LITERAL, Kind.NEW_ARRAY)) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(expr),
                            NodeUtils.getColumn(expr),
                            "Invalid assignment to non-array variable.",
                            null
                    ));
                } else {
                    var exprType = TypeUtils.getExprType(expr, table);

                    if (exprType == null) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(expr),
                                NodeUtils.getColumn(expr),
                                "Invalid assignment to variable. (Type not found)",
                                null
                        ));
                        return;
                    }

                    // If it's from import, ignore
                    if (table.getImports().contains(exprType.getName()) && Kind.check(expr, METHOD_EXPR)) {
                        return;
                    }

                    // If assigning a subclass to a superclass, ignore
                    if ((table.getSuper() != null && table.getSuper().equals(symbolType.getName())) && table.getClassName().equals(exprType.getName())) {
                        return;
                    }

                    // If both are imported, ignore
                    if (table.getImports().contains(exprType.getName()) && table.getImports().contains(symbolType.getName())) {
                        return;
                    }

                    if (!TypeUtils.areTypesAssignable(symbolType, exprType)) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(expr),
                                NodeUtils.getColumn(expr),
                                "Invalid assignment to variable. (Types are not compatible)",
                                null
                        ));
                    }
                }
            }
        }
}
