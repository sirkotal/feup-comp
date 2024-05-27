package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.METHOD_DECL;
import static pt.up.fe.comp2024.ast.Kind.THIS_EXPR;

public class InvalidOperations extends AnalysisVisitor {

        @Override
        public void buildVisitor() {
            addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
            addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
            addVisit(Kind.METHOD_EXPR, this::visitMethodExpr);
            addVisit(Kind.LENGTH_EXPR, this::visitLengthExpr);
            addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        }

        private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
            var left = binaryExpr.getChildren().get(0);
            var right = binaryExpr.getChildren().get(1);
            var operator = binaryExpr.get("op");

            var leftType = TypeUtils.getExprType(left, table);
            var rightType = TypeUtils.getExprType(right, table);

            if (leftType.isArray() || rightType.isArray()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryExpr),
                        NodeUtils.getColumn(binaryExpr),
                        "Binary operation '" + operator + "' cannot be applied to arrays.",
                        null
                ));
                return null;
            }

            switch (operator) {
                case "+":
                case "-":
                case "*":
                case "/":
                case "<":
                case "<=":
                case ">":
                case ">=":
                    if (!leftType.getName().equals(TypeUtils.getIntTypeName()) || !rightType.getName().equals(TypeUtils.getIntTypeName())) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(binaryExpr),
                                NodeUtils.getColumn(binaryExpr),
                                "Binary operation '" + operator + "' can only be applied to integers.",
                                null
                        ));
                    }
                    break;
                case "&&":
                case "||":
                    if (!leftType.getName().equals(TypeUtils.getBooleanTypeName()) || !rightType.getName().equals(TypeUtils.getBooleanTypeName())) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(binaryExpr),
                                NodeUtils.getColumn(binaryExpr),
                                "Binary operation '" + operator + "' can only be applied to booleans.",
                                null
                        ));
                    }
                    break;
                case "==":
                case "!=":
                    if (!leftType.equals(rightType)) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(binaryExpr),
                                NodeUtils.getColumn(binaryExpr),
                                "Binary operation '" + operator + "' can only be applied to operands of the same type.",
                                null
                        ));
                    }
                    break;
            }

            return null;
        }

        private Void visitArrayAccess(JmmNode arrayAccess, SymbolTable table) {
            var expr = arrayAccess.getJmmChild(0);
            if (!Kind.check(expr, Kind.VAR_REF_EXPR, Kind.ARRAY_LITERAL)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arrayAccess),
                        NodeUtils.getColumn(arrayAccess),
                        "Array access must be applied to a variable or an array literal.",
                        null
                ));
            }

            if (!TypeUtils.getExprType(expr, table).isArray()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arrayAccess),
                        NodeUtils.getColumn(arrayAccess),
                        "Array access must be applied to an array variable.",
                        null
                ));
            }

            var idxExpr = arrayAccess.getJmmChild(1);
            if (!TypeUtils.getExprType(idxExpr, table).getName().equals(TypeUtils.getIntTypeName())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arrayAccess),
                        NodeUtils.getColumn(arrayAccess),
                        "Array access index must be an integer.",
                        null
                ));
            }

            return null;
        }

        private Void visitMethodExpr(JmmNode methodExpr, SymbolTable table) {
            var methodName = methodExpr.get("name");
            var caller = methodExpr.getJmmChild(0);

            if (Kind.check(caller, Kind.VAR_REF_EXPR)) {
                var varType = TypeUtils.getExprType(caller, table);

                if (varType == null) {
                    if (table.getImports().contains(caller.get("name")) || table.getSuper() != null && table.getSuper().equals(caller.get("name"))) {
                        return null;
                    }
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(methodExpr),
                            NodeUtils.getColumn(methodExpr),
                            "Class " + caller.get("name") + " not found.",
                            null
                    ));
                    return null;
                }

                if (varType.getName().equals(table.getClassName()) && table.getSuper() == null) {
                    checkParams(table, methodExpr, methodName);
                }
            }

            if (Kind.check(caller, THIS_EXPR)) {
                var methodNode = caller.getAncestor(METHOD_DECL)
                        .orElseThrow(() -> new RuntimeException("`this` does not have a method ancestor."));

                // only main is static
                if (methodNode.get("name").equals("main")) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(methodExpr),
                            NodeUtils.getColumn(methodExpr),
                            "Cannot call a non-static method from a static context.",
                            null
                    ));
                    return null;
                }

                if (table.getSuper() == null) {
                    checkParams(table, methodExpr, methodName);
                }
            }

            return null;
        }

        private Void visitLengthExpr(JmmNode lengthExpr, SymbolTable table) {
            var array = lengthExpr.getJmmChild(0);
            var arrayType = TypeUtils.getExprType(array, table);

            if (!arrayType.isArray()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(lengthExpr),
                        NodeUtils.getColumn(lengthExpr),
                        "Length expression must be applied to an array.",
                        null
                ));
                return null;
            }

            return null;
        }

        private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
            var varName = varRefExpr.get("name");
            var isField = table.getFields().stream().anyMatch(f -> f.getName().equals(varName));

            var methodNode = varRefExpr.getAncestor(METHOD_DECL)
                    .orElseThrow(() -> new RuntimeException("Variable reference does not have a method ancestor."));

            // `main` is the only static method
            if (isField && methodNode.get("name").equals("main")) {
                var checkLocals = table.getLocalVariables(methodNode.get("name")).stream()
                        .anyMatch(l -> l.getName().equals(varName));

                if (checkLocals) {
                    return null;
                }

                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varRefExpr),
                        NodeUtils.getColumn(varRefExpr),
                        "Cannot access a field from a static context.",
                        null
                ));
            }
            return null;
        }

        // -----------------------------------
        // Utils
        // -----------------------------------

        private void checkParams(SymbolTable table, JmmNode methodExpr, String methodName) {
            if (table.getMethods().contains(methodName)) {
                var methodParams = table.getParameters(methodName);
                for (int i = 0; i < methodParams.size(); i++) {
                    var paramType = methodParams.get(i).getType();

                    var arg = methodExpr.getJmmChild(i + 1);
                    var argType = TypeUtils.getExprType(arg, table);
                    if (argType == null) {
                        return; // TODO: not sure
                    }

                    if (paramType.getObject("isVarargs", Boolean.class) && i == methodParams.size() - 1) {
                        if (!TypeUtils.areTypesAssignable(argType, paramType, table)) {
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(arg),
                                    NodeUtils.getColumn(arg),
                                    "Varargs needs to be integer, given " + argType.getName() + ".",
                                    null
                            ));
                            return;
                        }

                        if (argType.isArray() && i + 1 != methodExpr.getNumChildren() - 1) {
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(arg),
                                    NodeUtils.getColumn(arg),
                                    "Assigned array to varargs, but it is not the last parameter.",
                                    null
                            ));
                            return;
                        }
                        return;
                    } else {
                        if (!TypeUtils.areTypesAssignable(argType, paramType, table)) {
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(arg),
                                    NodeUtils.getColumn(arg),
                                    "Argument type mismatch in method '" + methodName + "'.",
                                    null
                            ));
                            return;
                        }
                    }
                }
            } else {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodExpr),
                        NodeUtils.getColumn(methodExpr),
                        "Method '" + methodName + "' is not defined in class '" + table.getClassName() + "'.",
                        null
                ));
            }

        }
}
