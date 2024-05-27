package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.nio.charset.StandardCharsets;

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

    private int ifCounter = 1;
    private int whileCounter = 1;

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
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);

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

        statement.append(END_STMT);
        return statement.toString();
    }
    private String visitVar(JmmNode jmmNode, Void unused) {
        StringBuilder statement = new StringBuilder();
        JmmNode parent = jmmNode.getParent();
        if (parent.getKind().equals("ClassDecl")) {
            statement.append(".field public ");
        }

        return statement.append(jmmNode.get("name"))
                .append(OptUtils.toOllirType(jmmNode.getJmmChild(0)))
                .append(END_STMT).toString();
    }

    private String visitAssignArrayStmt(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        var lhs = jmmNode.get("name");
        var idx = exprVisitor.visit(jmmNode.getJmmChild(0));
        var rhs = exprVisitor.visit(jmmNode.getJmmChild(1));

        code.append(idx.getComputation());
        code.append(rhs.getComputation());

        var method = jmmNode.getAncestor(METHOD_DECL).get();

        for (int i = 0; i < table.getParameters(method.get("name")).size(); i++) {
            var param = table.getParameters(method.get("name")).get(i);
            if (param.getName().equals(lhs)) {
                code.append("$").append(i + 1).append(".");
            }
        }

        code.append(lhs).append("[").append(idx.getCode()).append("].i32").append(SPACE)
                .append(ASSIGN).append(".i32").append(SPACE).append(rhs.getCode()).append(END_STMT);

        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var lhs = node.get("name");
        var rhs = exprVisitor.visit(node.getJmmChild(0));
        boolean flag = false;
        String new_rhs = null;

        boolean isField = table.getFields().stream()
                .anyMatch(f -> f.getName().equals(lhs)) &&
                table.getParameters(node.getAncestor(METHOD_DECL).get().get("name")).stream()
                        .noneMatch(f -> f.getName().equals(lhs))
                && table.getLocalVariables(node.getAncestor(METHOD_DECL).get().get("name")).stream()
                .noneMatch(f -> f.getName().equals(lhs));

        StringBuilder code = new StringBuilder();

        // TODO: will probably need to be reviewed, seems kinda jury-rigged
        if (node.getJmmChild(0).getKind().equals("MethodExpr")) {
            var check = node.getJmmChild(0).getChildren().subList(1, node.getChild(0).getNumChildren());

            for (var param : check) {
                if (param.getKind().equals("BinaryExpr")) {
                    var xpr = exprVisitor.visit(param);
                    var tmp = OptUtils.getTemp();
                    if (!xpr.getComputation().isEmpty()) {
                        code.append(xpr.getComputation());
                        System.out.println(xpr.getComputation());
                    }

                    Type resType = TypeUtils.getExprType(param, table);
                    String resOllirType = OptUtils.toOllirType(resType);
                    code.append(tmp).append(resOllirType).append(SPACE).append(ASSIGN).append(xpr.getCode()).append(END_STMT);

                    int first_comma = rhs.getCode().indexOf(',');
                    int second_comma = rhs.getCode().indexOf(',', first_comma + 1);
                    int start = second_comma + 1;
                    int end = rhs.getCode().indexOf(')');
                    String subToModify = rhs.getCode().substring(start, end);

                    new_rhs = rhs.getCode().replace(subToModify, (tmp + resOllirType));
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
            code.append(temp).append(typeString).append(SPACE).append(ASSIGN);
            if (!Kind.check(node.getJmmChild(0), BINARY_EXPR)) {
                code.append(typeString).append(SPACE);
            }
            code.append(rhs.getCode()).append(END_STMT);
            code.append("putfield(this, ").append(lhs).append(typeString).append(", ").append(temp).append(typeString).append(").V").append(END_STMT);
        } else {
            code.append(lhs).append(typeString).append(SPACE)
                    .append(ASSIGN).append(typeString).append(SPACE);
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

    private String visitParam(JmmNode node, Void unused) {
        return node.get("name") + OptUtils.toOllirType(node.getJmmChild(0));
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        OllirExprGeneratorVisitor alpha = new OllirExprGeneratorVisitor(this.table);
        alpha.buildVisitor();
        var alpha_visit = alpha.visit(node.getJmmChild(0), unused);
        String computation = alpha_visit.getComputation();
        String code = alpha_visit.getCode();

        return computation + code + END_STMT;
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
            code.append(OptUtils.toOllirType(node.getJmmChild(0))).append(L_BRACKET);
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
            //System.out.println("child c = " + childCode);
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

                code.append("ret")
                        .append(OptUtils.toOllirType(returnExpr.getParent().getJmmChild(0)))
                        .append(SPACE).append(tempReg).append(funcType).append(END_STMT);
            } else {
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

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        String ifLabel = "if" + this.ifCounter++;

        var condition = exprVisitor.visit(node.getJmmChild(0));
        var trueBlock = visit(node.getJmmChild(1));
        var falseBlock = visit(node.getJmmChild(2));

        computation.append(condition.getComputation());

        if (Kind.check(node.getJmmChild(0), BOOLEAN_LITERAL, VAR_REF_EXPR)) {
            code.append("if (").append(condition.getCode()).append(") goto ").append(ifLabel).append(END_STMT);
        } else {
            var tempVar = OptUtils.getTemp();
            code.append(tempVar).append(".bool").append(SPACE).append(ASSIGN).append(".bool").append(SPACE)
                    .append(condition.getCode()).append(END_STMT);

            code.append("if (").append(tempVar).append(".bool) goto ").append(ifLabel).append(END_STMT);
        }

        code.append(falseBlock);
        code.append("goto end").append(ifLabel).append(END_STMT);
        code.append(ifLabel).append(":").append(NL);
        code.append(trueBlock);
        code.append("end").append(ifLabel).append(":").append(NL);

        return computation.toString() + code;
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        var condition = exprVisitor.visit(node.getJmmChild(0));
        var block = visit(node.getJmmChild(1));

        computation.append(condition.getComputation());

        String whileCond = "whileCond" + this.whileCounter;
        String whileLoop = "whileLoop" + this.whileCounter;
        String whileEnd = "whileEnd" + this.whileCounter;
        this.whileCounter++;

        code.append(whileCond).append(":").append(NL);

        if (Kind.check(node.getJmmChild(0), BOOLEAN_LITERAL, VAR_REF_EXPR)) {
            code.append("if (").append(condition.getCode()).append(") goto ").append(whileLoop).append(END_STMT);
        } else {
            var tempVar = OptUtils.getTemp();
            code.append(tempVar).append(".bool").append(SPACE).append(ASSIGN).append(".bool").append(SPACE)
                    .append(condition.getCode()).append(END_STMT);

            code.append("if (").append(tempVar).append(".bool) goto ").append(whileLoop).append(END_STMT);
        }

        code.append("goto ").append(whileEnd).append(END_STMT);
        code.append(whileLoop).append(":").append(NL);
        code.append(block);
        code.append("goto ").append(whileCond).append(END_STMT);
        code.append(whileEnd).append(":").append(NL);

        return computation.toString() + code;
    }

    private String visitBlockStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

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
