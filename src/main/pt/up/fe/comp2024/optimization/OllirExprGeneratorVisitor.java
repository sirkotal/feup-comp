package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private static final String END_STMT = ";\n";
    private static final String NL = "\n";

    private final SymbolTable table;

    private int andCount = 0;
    private int varargsCount = 0;
    private int arraysCount = 0;

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
        addVisit(NEW_ARRAY, this::visitNewArray);
        addVisit(NEGATION, this::visitNegation);
        addVisit(LENGTH_EXPR, this::visitLenghtExpr);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(ARRAY_LITERAL, this::visitArrayLiteral);

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
        String code = (node.get("value").equals("true") ? "1" : "0") + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitNegation(JmmNode node, Void unused) {
        var child = visit(node.getJmmChild(0));
        String code = "!.bool " + child.getCode();
        return new OllirExprResult(code, child.getComputation());
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();
        Type type = TypeUtils.getExprType(node, table);

        if (node.get("op").equals("&&")) {
            String tempReg = OptUtils.getTemp();

            computation.append("if(").append(lhs.getCode()).append(") goto ")
                    .append("true_").append(andCount).append(END_STMT);

            computation.append(lhs.getComputation());
            computation.append(tempReg).append(".bool").append(SPACE).append(ASSIGN).append(".bool")
                    .append(SPACE).append("0.bool").append(END_STMT);

            computation.append("goto ").append("end_").append(andCount).append(END_STMT);

            computation.append("true_").append(andCount).append(":").append(NL);

            computation.append(rhs.getComputation());
            computation.append(tempReg).append(".bool").append(SPACE).append(ASSIGN).append(".bool")
                    .append(SPACE).append(rhs.getCode()).append(END_STMT);

            computation.append("end_").append(andCount).append(":").append(NL);

            this.andCount++;
            return new OllirExprResult(tempReg + ".bool", computation);
        }

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

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

        code = lhs.getCode() + SPACE + node.get("op")
                + OptUtils.toOllirType(type) + SPACE + rhs.getCode();
        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        Type type = TypeUtils.getExprType(node, table);
        if (type == null) return OllirExprResult.EMPTY;

        var method = node.getAncestor(METHOD_DECL).get();

        boolean isField = table.getFields().stream()
                .anyMatch(f -> f.getName().equals(id)) &&
                table.getParameters(method.get("name")).stream()
                        .noneMatch(p -> p.getName().equals(id)) &&
                table.getLocalVariables(method.get("name")).stream()
                .noneMatch(l -> l.getName().equals(id));

        String typeCode = OptUtils.toOllirType(type);

        if (isField) {
            String tempReg = OptUtils.getTemp();
            StringBuilder computation = new StringBuilder();
            computation.append(tempReg)
                    .append(typeCode).append(SPACE).append(ASSIGN).append(typeCode).append(SPACE)
                    .append("getfield(this, ").append(id).append(typeCode).append(")").append(typeCode).append(END_STMT);
            return new OllirExprResult(tempReg + typeCode, computation);
        }

        StringBuilder code = new StringBuilder();

        for (int i = 0; i < table.getParameters(method.get("name")).size(); i++) {
            var param = table.getParameters(method.get("name")).get(i);
            if (param.getName().equals(id)) {
                code.append("$").append(i + 1).append(".");
            }
        }
        code.append(id).append(typeCode);

        return new OllirExprResult(code.toString());
    }

    private OllirExprResult visitMethodExpr(JmmNode node, Void unused) {
        var caller = node.getJmmChild(0);
        var funcName = node.get("name");
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        if (Kind.check(caller, PRIORITY)) {
            caller = caller.getJmmChild(0);
        }

        // compare caller.get("name) with last part of the imports (test.a.b.c -> c)
        var importedNames = table.getImports().stream().map(
                s -> s.substring(s.lastIndexOf(".") + 1)
        ).toList();

        if (importedNames.contains(caller.get("name")) || table.getSuper() != null && table.getSuper().equals(caller.get("name"))) {
            code.append("invokestatic(");
        } else {
            code.append("invokevirtual(");
        }

        if (Kind.check(caller, THIS_EXPR) || table.getImports().contains(caller.get("name")) || (table.getSuper() != null && table.getSuper().equals(caller.get("name")))) {
            code.append(caller.get("name"));
        } else {
            code.append(visit(caller).getCode());
        }

        code.append(", \"").append(funcName).append("\"");

        var params = node.getChildren().subList(1, node.getNumChildren());
        var funcParams = table.getMethods().contains(funcName) ?
                table.getParameters(funcName) : new ArrayList<Symbol>();
        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            var visiting = visit(param);
            computation.append(visiting.getComputation());

            if (!funcParams.isEmpty()
                    && ((Boolean) funcParams.get(funcParams.size() - 1).getType().getObject("isVarargs"))
                    && i >= funcParams.size() - 1) {

                if (i == funcParams.size() - 1) {
                    computation.append("varargs_").append(varargsCount).append(".array.i32")
                            .append(SPACE).append(ASSIGN).append(".array.i32").append(SPACE)
                            .append("new(array, ").append(params.size() - funcParams.size() + 1).append(".i32).array.i32").append(END_STMT);
                }

                computation.append("varargs_").append(varargsCount).append(".array.i32")
                        .append("[").append(i - funcParams.size() + 1).append(".i32].i32")
                        .append(SPACE).append(ASSIGN).append(".i32").append(SPACE).append(visiting.getCode()).append(END_STMT);
            } else if (Kind.check(param, BINARY_EXPR, METHOD_EXPR, NEW_CLASS, ARRAY_ACCESS, NEW_ARRAY)) {
                var temp = OptUtils.getTemp();
                var type = OptUtils.toOllirType(TypeUtils.getExprType(param, table));

                computation.append(temp).append(type).append(SPACE)
                        .append(ASSIGN).append(type).append(SPACE)
                        .append(visiting.getCode()).append(END_STMT);

                code.append(", ").append(temp).append(type);
            } else {
                code.append(", ").append(visiting.getCode());
            }
        }
        if (!funcParams.isEmpty() && ((Boolean) funcParams.get(funcParams.size() - 1).getType().getObject("isVarargs"))) {
            code.append(", varargs_").append(varargsCount).append(".array.i32");
            this.varargsCount++;
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

        if (Kind.check(node.getParent(), BINARY_EXPR)) {
            var funcType = OptUtils.toOllirType(table.getReturnType(funcName));
            var tempReg = OptUtils.getTemp();

            computation.append(tempReg)
                    .append(funcType).append(SPACE).append(ASSIGN).append(funcType).append(SPACE)
                    .append(code).append(END_STMT);

            return new OllirExprResult(tempReg + funcType, computation);
        }

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitNewClass(JmmNode node, Void unused) {
        var className = node.get("name");
        return new OllirExprResult("new (" + className + ")." + className);
    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        var sizeCode = visit(node.getJmmChild(0)).getCode();
        String tempReg = OptUtils.getTemp();

        StringBuilder computation = new StringBuilder();
        computation.append(tempReg).append(".i32").append(SPACE).append(ASSIGN)
                .append(".i32").append(SPACE).append(sizeCode).append(END_STMT);

        String code = "new(array, " + tempReg + ".i32).array.i32";

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitLenghtExpr(JmmNode node, Void unused) {
        var array = visit(node.getJmmChild(0));
        String tempReg = OptUtils.getTemp();

        StringBuilder computation = new StringBuilder();
        computation.append(array.getComputation());

        computation.append(tempReg).append(".i32").append(SPACE).append(ASSIGN)
                .append(".i32").append(SPACE).append("arraylength(").append(array.getCode()).append(").i32").append(END_STMT);

        return new OllirExprResult(tempReg + ".i32", computation);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var array = visit(node.getJmmChild(0));
        var index = visit(node.getJmmChild(1));

        computation.append(array.getComputation());
        computation.append(index.getComputation());

        if (Kind.check(node.getJmmChild(1), METHOD_EXPR, BINARY_EXPR, ARRAY_ACCESS)) {
            var tempReg = OptUtils.getTemp();

            computation.append(tempReg).append(".i32").append(SPACE).append(ASSIGN).append(".i32").append(SPACE)
                    .append(index.getCode()).append(END_STMT);

            code.append(array.getCode()).append("[").append(tempReg).append(".i32").append("].i32");
        } else {
            code.append(array.getCode()).append("[").append(index.getCode()).append("].i32");
        }

        return new OllirExprResult(
                code.toString(),
                computation
        );
    }

    private OllirExprResult visitArrayLiteral(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var elements = node.getChildren();

        computation.append("__varargs_array_0").append(".array.i32")
                .append(SPACE).append(ASSIGN).append(".array.i32").append(SPACE)
                .append("new(array, ").append(elements.size()).append(".i32).array.i32").append(END_STMT);

        for (int i = 0; i < elements.size(); i++) {
            var e = elements.get(i);
            var visiting = visit(e);
            computation.append("__varargs_array_").append(arraysCount).append(".array.i32").append("[" + i + ".i32].i32")
                    .append(SPACE).append(ASSIGN).append(".array.i32").append(SPACE)
                    .append(visiting.getCode()).append(END_STMT);
        }

        code.append("__varargs_array_0").append(".array.i32");

        //System.out.println(code);
        //System.out.println(computation);

        arraysCount += 1;

        return new OllirExprResult(
                code.toString(),
                computation
        );
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
