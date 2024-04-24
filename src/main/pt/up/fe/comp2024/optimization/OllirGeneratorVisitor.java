package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVar);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(ASSIGN_ARRAY_STMT, this::visitAssignArrayStmt);
        addVisit(INTEGER_LITERAL, this::visitIntLiteral);
        addVisit(BOOLEAN_LITERAL, this::visitBoolLiteral);
        addVisit(VAR_REF_EXPR, this::visitVarRef);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitImport(JmmNode jmmNode, Void unused) {
        StringBuilder statement = new StringBuilder();
        statement.append("import ");
        var value = jmmNode.get("value").substring(1, jmmNode.get("value").length() - 1);
        var lst = value.split(", ");
        var beta = String.join(".", lst);
        for (String import_id : table.getImports()) {
            if (beta.equals(import_id)) {
                statement.append(import_id);
            }
        }

        statement.append(";\n");
        //System.out.println(statement.toString());
        return statement.toString();
    }
    private String visitVar(JmmNode jmmNode, Void unused) {
        StringBuilder statement = new StringBuilder();
        JmmNode parent = jmmNode.getJmmParent();
        // System.out.println(parent.getKind());
        if (parent.getKind().equals("ClassDecl")) {
            statement.append(".field public ");
        }

        var id = jmmNode.get("name");
        // System.out.println(id);
        statement.append(id);

        var typeName = jmmNode.getJmmChild(0).get("name");

        if (typeName.equals("int")) {
            statement.append(".i32;\n");
        }
        else if (typeName.equals("boolean")) {
            statement.append(".bool;\n");
        }
        else {
            statement.append("." + typeName + ";\n");
        }

        return statement.toString();
    }

    private String visitAssignArrayStmt(JmmNode jmmNode, Void unused) {
        return new String();
    }

    private String visitIntLiteral(JmmNode jmmNode, Void unused) {
        OllirExprGeneratorVisitor alpha = new OllirExprGeneratorVisitor(this.table);
        alpha.buildVisitor();
        String omega = alpha.visit(jmmNode, unused).getCode();
        // System.out.println(omega);
        return omega;
    }

    private String visitBoolLiteral(JmmNode jmmNode, Void unused) {
        OllirExprGeneratorVisitor alpha = new OllirExprGeneratorVisitor(this.table);
        alpha.buildVisitor();
        String omega = alpha.visit(jmmNode, unused).getCode();
        // System.out.println(omega);
        return omega;
    }

    private String visitVarRef(JmmNode jmmNode, Void unused) {
        OllirExprGeneratorVisitor alpha = new OllirExprGeneratorVisitor(this.table);
        alpha.buildVisitor();
        String omega = alpha.visit(jmmNode, unused).getCode();
        //System.out.println(omega);
        return omega;
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var lhs = node.get("name");
        //System.out.println(lhs);
        var rhs = exprVisitor.visit(node.getJmmChild(0));
        boolean flag = false;
        String new_rhs = null;
        //System.out.println(rhs);

        // System.out.println(node.getJmmChild(0).getKind());

        boolean isField = table.getFields().stream()
                .anyMatch(f -> f.getName().equals(lhs));

        StringBuilder code = new StringBuilder();

        // TODO: will probably need to be reviewed, seems kinda jury-rigged
        if (node.getJmmChild(0).getKind().equals("MethodExpr")) {
            //System.out.println("hello");
            var check = node.getJmmChild(0).getChildren().subList(1, node.getChild(0).getNumChildren());

            for (var param : check) {
                //System.out.println(param.getKind());
                //System.out.println(exprVisitor.visit(param));

                if (param.getKind().equals("BinaryExpr")) {
                    var xpr = exprVisitor.visit(param);
                    var tmp = OptUtils.getTemp();
                    Type resType = TypeUtils.getExprType(param, table);
                    String resOllirType = OptUtils.toOllirType(resType);
                    code.append(tmp).append(resOllirType).append(SPACE).append(ASSIGN).append(xpr.getCode()).append(END_STMT);
                    // code.append(exprVisitor.visit(param)).append(END_STMT);
                    new_rhs = rhs.getCode().replace(xpr.getCode(), (tmp + resOllirType));
                    flag = true;
                }
            }
        }

        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        if (isField) {
            String temp = OptUtils.getTemp();
            code.append(temp).append(typeString).append(SPACE).append(ASSIGN).append(typeString).append(SPACE)
                    .append(flag ? new_rhs : rhs.getCode()).append(END_STMT);
            code.append("putfield(this, ").append(lhs).append(typeString).append(", ").append(temp).append(typeString).append(").V").append(END_STMT);
        } else {
            code.append(lhs)
                    .append(typeString)
                    .append(SPACE)
                    .append(ASSIGN);

            var kindsToAddType = Kind.check(node.getChild(0), VAR_REF_EXPR, INTEGER_LITERAL, BOOLEAN_LITERAL, THIS_EXPR, NEW_CLASS, METHOD_EXPR);
            if (kindsToAddType || !rhs.getCode().contains(typeString)) {
                code.append(typeString).append(SPACE);
            }
        }

        if (!isField) {
            if (flag) {
                code.append(new_rhs);
            } else {
                code.append(rhs.getCode());
            }
            code.append(END_STMT);
        }

        if (Kind.check(node.getJmmChild(0), NEW_CLASS)) {
            code.append("invokespecial(").append(lhs)
                    .append(typeString)
                    .append(",\"<init>\").V").append(END_STMT);
        }
        return code.toString();
    }

    // TODO: Code here for reference, to delete later
//    private String visitReturn(JmmNode node, Void unused) {
//
//        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
//        Type retType = table.getReturnType(methodName);
//
//        StringBuilder code = new StringBuilder();
//
//        var expr = OllirExprResult.EMPTY;
//
//        if (node.getNumChildren() > 0) {
//            expr = exprVisitor.visit(node.getJmmChild(0));
//        }
//
//        code.append(expr.getComputation());
//        code.append("ret");
//        // System.out.println(code);
//        if (node.get("name").equals("main")) {
//            code.append(".V");
//            return code.toString();
//        }
//        code.append(OptUtils.toOllirType(retType));
//        code.append(SPACE);
//
//        code.append(expr.getCode());
//
//        code.append(END_STMT);
//
//        return code.toString();
//    }


    private String visitParam(JmmNode node, Void unused) {

        var typeName = node.getJmmChild(0).get("name");
        String typeCode = null;

        if (typeName.equals("int")) {
            typeCode = ".i32";
        }
        else if (typeName.equals("boolean")) {
            typeCode = ".bool";
        }
        else {
            typeCode = "." + typeName;
        }
        var id = node.get("name");

        return id + typeCode;
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        OllirExprGeneratorVisitor alpha = new OllirExprGeneratorVisitor(this.table);
        alpha.buildVisitor();
        String code = alpha.visit(node.getJmmChild(0), unused).getCode();

        return code + END_STMT;
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        if (name.equals("main")) {
            code.append("static ");
        }
        code.append(name);

        // param
        var afterParam = 1;
        if (!name.equals("main") && (!node.getChildren().isEmpty() && Kind.check(node.getChild(1), PARAM))) {
            code.append("(");
            var childParam = node.getJmmChild(afterParam);
            while (Kind.check(childParam, PARAM)) {
                var paramCode = visit(childParam);
                // System.out.println(paramCode);
                code.append(paramCode);
                if (!paramCode.isEmpty()) {
                    code.append(", ");
                }
                childParam = node.getJmmChild(++afterParam);
            }
            code.delete(code.length() - 2, code.length()); // remove last ", "
            code.append(")");
        } else {
            code.append("(");
            if (node.get("name").equals("main")) {
                code.append("args.array.String");
                afterParam = 0;
            }
            code.append(")");
        }

        // type
        if (!node.getChildren().isEmpty() && !node.get("name").equals("main")) {
            var retType = node.getJmmChild(0).get("name");
            if (retType.equals("int")) {
                code.append(".i32");
            }
            else if (retType.equals("boolean")) {
                code.append(".bool");
            }
            else {
                code.append("." + retType);
            }
            code.append(L_BRACKET);
        } else {
            if (node.get("name").equals("main")) {
                code.append(".V");
            }
            code.append(L_BRACKET);
        }


        // rest of its children stmts
        for (int i = afterParam; i < (node.get("name").equals("main") ? node.getNumChildren() : node.getNumChildren() - 1); i++) {
            var child = node.getJmmChild(i);

            if (Kind.check(child, VAR_DECL)) {
                continue;
            }

            var childCode = visit(child);
            code.append(childCode);
        }

        // return stmt
        if (!node.getChildren().isEmpty() && !node.get("name").equals("main")) {
            var returnExpr = node.getJmmChild(node.getNumChildren() - 1);

            //System.out.println(returnExpr.getParent().getJmmChild(0).get("name"));
            //var retType = node.getJmmChild(0).get("name");
            //System.out.println(returnExpr.getKind());

            // TODO: Hacky but it should work
            var exprVisitor = new OllirExprGeneratorVisitor(this.table);
            exprVisitor.buildVisitor();
            var omega = exprVisitor.visit(returnExpr, unused);
            // System.out.println(omega);
            code.append(omega.getComputation());

            //System.out.println(OptUtils.toOllirType(TypeUtils.getExprType(returnExpr, table)));

            if (!OptUtils.toOllirType(TypeUtils.getExprType(returnExpr, table)).equals(".i32") && !OptUtils.toOllirType(TypeUtils.getExprType(returnExpr, table)).equals(".bool") && omega.getCode().contains("invokevirtual")) {
                var funcType = OptUtils.toOllirType(TypeUtils.getExprType(returnExpr, table));
                var tempReg = OptUtils.getTemp();

                code.append(tempReg)
                        .append(funcType).append(SPACE).append(ASSIGN).append(funcType).append(SPACE)
                        .append(omega.getCode()).append(END_STMT);

                // retType
                String typeRet;
                if (returnExpr.getParent().getJmmChild(0).get("name").equals("int")) {
                    typeRet = ".i32";
                }
                else if (returnExpr.getParent().getJmmChild(0).get("name").equals("boolean")) {
                    typeRet = ".bool";
                }
                else {
                    typeRet = "." + returnExpr.getParent().getJmmChild(0).get("name");
                }
                code.append("ret")
                        .append(typeRet)
                        .append(SPACE)
                        .append(tempReg + funcType)
                        .append(END_STMT);
            }
            else {
                code.append("ret")
                        .append(OptUtils.toOllirType(TypeUtils.getExprType(returnExpr, table)))
                        .append(SPACE)
                        .append(omega.getCode())
                        .append(END_STMT);
            }
        }

        if (node.get("name").equals("main")) {
            code.append("ret.V;\n");
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        if (table.getSuper() != null && !table.getSuper().equals("")) {
            code.append(" extends ");
            code.append(table.getSuper());
        }
        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
