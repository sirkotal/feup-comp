package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(METHOD_EXPR, this::visitMethodExpr);
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(NEW_CLASS, this::visitNewClass);

        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        // System.out.println(ollirIntType);
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var boolType = new Type(TypeUtils.getBooleanTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String code = node.get("value") + ollirBoolType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type type = TypeUtils.getExprType(node, table);
        String code;
        if (Kind.check(node.getParent(), BINARY_EXPR, METHOD_DECL)) {
            Type resType = TypeUtils.getExprType(node, table);
            String resOllirType = OptUtils.toOllirType(resType);
            code = OptUtils.getTemp() + resOllirType;

            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE)
                    .append(lhs.getCode()).append(SPACE)
                    .append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);

            return new OllirExprResult(code, computation);
        }

        var lhsType = OptUtils.toOllirType(TypeUtils.getExprType(node.getJmmChild(0), table));
        var rhsType = OptUtils.toOllirType(TypeUtils.getExprType(node.getJmmChild(1), table));
        code = lhsType + SPACE + lhs.getCode() + SPACE + node.get("op")
                + rhsType + SPACE + rhs.getCode();
        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        Type type = TypeUtils.getExprType(node, table);
        if (type == null) return OllirExprResult.EMPTY;

        boolean isField = table.getFields().stream()
                .anyMatch(f -> f.getName().equals(id));

        String typeCode = OptUtils.toOllirType(type);

        if (isField) {
            String tempReg = OptUtils.getTemp();
            StringBuilder computation = new StringBuilder();
            computation.append(tempReg)
                    .append(typeCode).append(SPACE).append(ASSIGN).append(typeCode).append(SPACE)
                    .append("getfield(this, ").append(id).append(typeCode).append(")").append(typeCode).append(END_STMT);
            return new OllirExprResult(tempReg + typeCode, computation);
        }

        return new OllirExprResult(id + typeCode);
    }

    private OllirExprResult visitMethodExpr(JmmNode node, Void unused) {
        var caller = node.getJmmChild(0);
        var funcName = node.get("name");
        StringBuilder code = new StringBuilder();

        if (table.getImports().contains(caller.get("name")) || table.getSuper() != null && table.getSuper().equals(caller.get("name"))) {
            code.append("invokestatic(");
        } else {
            code.append("invokevirtual(");
        }

        // System.out.println(caller.get("name"));

        if (Kind.check(caller, THIS_EXPR) || table.getImports().contains(caller.get("name")) || (table.getSuper() != null && table.getSuper().equals(caller.get("name")))) {
            code.append(caller.get("name"));
        } else {
            code.append(visit(caller).getCode());
        }

        code.append(", \"").append(funcName).append("\"");

        var params = node.getChildren().subList(1, node.getNumChildren());
        for (var param : params) {
            code.append(", ").append(visit(param).getCode());
        }
        code.append(")");

        if (Kind.check(caller, THIS_EXPR, VAR_REF_EXPR)) {
            boolean checkVarRef = Kind.check(caller, VAR_REF_EXPR) &&
                    (table.getImports().contains(caller.get("name")) ||
                    (table.getSuper() != null && table.getSuper().equals(caller.get("name"))));

            boolean checkThis = Kind.check(caller, THIS_EXPR) && !table.getMethods().contains(funcName) && table.getSuper() != null;

            if (!(checkVarRef || checkThis)) {
                code.append(OptUtils.toOllirType(table.getReturnType(funcName)));
            } else {
                code.append(".V");
            }
        } else {
            code.append(".V");
        }

        StringBuilder computation = new StringBuilder();
        if (Kind.check(node.getParent(), BINARY_EXPR)) {
            var funcType = OptUtils.toOllirType(table.getReturnType(funcName));
            var tempReg = OptUtils.getTemp();

            computation.append(tempReg)
                    .append(funcType).append(SPACE).append(ASSIGN).append(funcType).append(SPACE)
                    .append(code).append(END_STMT);

            return new OllirExprResult(tempReg + funcType, computation);
        }

        return new OllirExprResult(code.toString());
    }

    private OllirExprResult visitNewClass(JmmNode node, Void unused) {
        var className = node.get("name");
        return new OllirExprResult("new (" + className + ")." + className);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
