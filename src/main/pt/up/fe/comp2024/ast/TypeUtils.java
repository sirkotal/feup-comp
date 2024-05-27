package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.Optional;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final String STRING_TYPE_NAME = "String";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static String getBooleanTypeName() {
        return BOOLEAN_TYPE_NAME;
    }

    public static String getStringTypeName() {
        return STRING_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());
        return switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case METHOD_EXPR -> getMethodExprType(expr, table);
            case INTEGER_LITERAL, LENGTH_EXPR -> new Type(INT_TYPE_NAME, false);
            case BOOLEAN_LITERAL, NEGATION -> new Type(BOOLEAN_TYPE_NAME, false);
            case NEW_ARRAY -> new Type(INT_TYPE_NAME, true);
            case ARRAY_LITERAL -> getArrayLiteralType(expr, table);
            case ARRAY_ACCESS -> getArrayAccess(expr, table);
            case ARRAY_TYPE -> new Type(expr.getJmmChild(0).get("name"), true);
            case CLASS_DECL, NEW_CLASS -> new Type(expr.get("name"), false);
            case PRIORITY -> getExprType(expr.getJmmChild(0), table);
            case THIS_EXPR -> new Type(table.getClassName(), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "-", "*", "/" -> new Type(INT_TYPE_NAME, false);
            case "<", "<=", ">", ">=", "&&", "||", "==", "!=" -> new Type(BOOLEAN_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        var varName = varRefExpr.get("name");
        var methodDecl = varRefExpr.getAncestor(Kind.METHOD_DECL);

        return findTypeInTable(varName, table, methodDecl);
    }

    private static Type getMethodExprType(JmmNode methodExpr, SymbolTable table) {
        var variable = methodExpr.getJmmChild(0);

        if (Kind.check(variable, Kind.VAR_REF_EXPR)) {
            var type = getVarExprType(variable, table);
            if (type == null) {
                return null;
            }

            var importedNames = table.getImports().stream().map(
                    s -> s.substring(s.lastIndexOf(".") + 1)
            ).toList();

            if (type.getName().equals(table.getClassName())) {
                var methodName = methodExpr.get("name");
                if (table.getMethods().contains(methodName)) {
                    return table.getReturnType(methodName);
                } else {
                    throw new RuntimeException("Method '" + methodName + "' not found in class");
                }
            } else if (importedNames.contains(type.getName()) || table.getSuper() != null && table.getSuper().equals(type.getName())) {
                var assignStmt = methodExpr.getAncestor(Kind.ASSIGN_STMT);
                if (assignStmt.isPresent()) {
                    var assignTo = assignStmt.get().get("name");
                    return findTypeInTable(assignTo, table, methodExpr.getAncestor(Kind.METHOD_DECL));
                } else {
                    var assignArrayStmt = methodExpr.getAncestor(Kind.ASSIGN_ARRAY_STMT);
                    if (assignArrayStmt.isPresent()) {
                        var assignToArray = assignArrayStmt.get().get("name");
                        return findTypeInTable(assignToArray, table, methodExpr.getAncestor(Kind.METHOD_DECL));
                    }
                }
            } else {
                return null;
            }

            return type;
        }

        var methodName = methodExpr.get("name");
        if (Kind.check(variable, Kind.THIS_EXPR)) {
            if (table.getMethods().contains(methodName)) {
                return table.getReturnType(methodName);
            } else {
                throw new RuntimeException("Method '" + methodName + "' not found in class");
            }
        }

        if (Kind.check(variable, Kind.PRIORITY)) {
            var expr = variable.getJmmChild(0);
            var prioType = getExprType(expr, table);

            var importedNames = table.getImports().stream().map(
                    s -> s.substring(s.lastIndexOf(".") + 1)
            ).toList();

            if (prioType.getName().equals(table.getClassName())) {
                if (table.getMethods().contains(methodName)) {
                    return table.getReturnType(methodName);
                } else {
                    throw new RuntimeException("Method '" + methodName + "' not found in class");
                }
            } else if (importedNames.contains(expr.get("name")) || table.getSuper() != null && table.getSuper().equals(expr.get("name"))) {
                var assignStmt = methodExpr.getAncestor(Kind.ASSIGN_STMT);
                if (assignStmt.isPresent()) {
                    var assignTo = assignStmt.get().get("name");
                    return findTypeInTable(assignTo, table, methodExpr.getAncestor(Kind.METHOD_DECL));
                } else {
                    var assignArrayStmt = methodExpr.getAncestor(Kind.ASSIGN_ARRAY_STMT);
                    if (assignArrayStmt.isPresent()) {
                        var assignToArray = assignArrayStmt.get().get("name");
                        return findTypeInTable(assignToArray, table, methodExpr.getAncestor(Kind.METHOD_DECL));
                    }
                }
            } else {
                return null;
            }
        }

        throw new RuntimeException("Method expression not supported");
    }

    public static Type getVarDeclType(JmmNode varDecl) {
        boolean isArray = varDecl.getJmmChild(0).getObject("isArray", Boolean.class);
        String typeName;

        if (isArray) {
            typeName = varDecl.getJmmChild(0).getJmmChild(0).get("name");
        } else {
            typeName = varDecl.getJmmChild(0).get("name");
        }

        return new Type(typeName, isArray);
    }

    public static Type getArrayLiteralType(JmmNode arrayLiteral, SymbolTable table) {
        // TODO: May have some error if the array is empty (ArrayLiteral: [])
        var elementsType = arrayLiteral.getChildren().stream()
                .map(element -> getExprType(element, table))
                .toList();

        if (elementsType.isEmpty()) {
            throw new RuntimeException("Array is empty");
        } else if (elementsType.size() == 1) {
            return new Type(elementsType.get(0).getName(), true);
        } else {
            return new Type(
                    elementsType.stream().reduce((type1, type2) -> {
                        if (!areTypesAssignable(type1, type2, table)) {
                            throw new RuntimeException("Array elements have different types");
                        }
                        return type1;
                    }).get().getName(),
                    true
            );
        }
    }

    private static Type findTypeInTable(String name, SymbolTable table, Optional<JmmNode> methodDecl) {
        if (methodDecl.isPresent()) {
            var methodName = methodDecl.get().get("name");
            var type = table.getLocalVariables(methodName).stream()
                    .filter(var -> var.getName().equals(name))
                    .map(Symbol::getType)
                    .findFirst()
                    .or(
                            () -> table.getParameters(methodName).stream()
                                    .filter(var -> var.getName().equals(name))
                                    .map(Symbol::getType)
                                    .findFirst()
                                    .or(
                                            () -> table.getFields().stream()
                                                    .filter(var -> var.getName().equals(name))
                                                    .map(Symbol::getType)
                                                    .findFirst()
                                    )
                    );

            return type.orElse(null);
        } else {
            throw new RuntimeException("Variable reference outside of method declaration");
        }
    }

    private static Type getArrayAccess(JmmNode arrayAccess, SymbolTable table) {
        var arrayType = getExprType(arrayAccess.getJmmChild(0), table);

        return new Type(arrayType.getName(), false);
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType, SymbolTable table) {
        if (table.getSuper() != null && sourceType.getName().equals(table.getClassName()) && destinationType.getName().equals(table.getSuper())) {
            return true;
        }

        return sourceType.getName().equals(destinationType.getName());
    }
}
