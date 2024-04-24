package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        var accessSpec = classUnit.getClassAccessModifier() != AccessModifier.DEFAULT ?
                classUnit.getClassAccessModifier().name().toLowerCase() + " " :
                "public ";
        code.append(".class ").append(accessSpec).append(className).append(NL).append(NL);

        // TODO: Hardcoded to Object, needs to be expanded
        if (classUnit.getSuperClass() == null) {
            code.append(".super java/lang/Object").append(NL);

            // generate a single constructor method
            var defaultConstructor = """
                    ;default constructor
                    .method public <init>()V
                        aload_0
                        invokespecial java/lang/Object/<init>()V
                        return
                    .end method
                    """;
            code.append(defaultConstructor);
        } else {
            code.append(".super ").append(classUnit.getSuperClass()).append(NL);

            var customConstructor = String.format("""
                    ;default constructor
                    .method public <init>()V
                        aload_0
                        invokespecial %s/<init>()V
                        return
                    .end method
                    """, classUnit.getSuperClass());
            code.append(customConstructor);
        }

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();

        // TODO: Hardcoded param types and return type, needs to be expanded
        // code.append("\n.method ").append(modifier).append(methodName).append("(I)I").append(NL);

        if (methodName.equals("main")) {
            code.append("\n.method ").append(modifier).append("static ").append(methodName).append("(");
        } else {
            code.append("\n.method ").append(modifier).append(methodName).append("(");
        }

        for (var param : method.getParams()) {
            // TODO: remove if when array types are implemented
            if (param.getType().toString().equals("STRING[]")) {
                code.append("[Ljava/lang/String;");
            } else {
                code.append(this.getType(param.getType()));
            }
        }

        code.append(")").append(this.getType(method.getReturnType())).append(NL);

        // Add limits (TODO: remove hardcoded values)
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        code.append(this.store(operand));
        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // Same as `this.store()` but for loading values
        return switch (operand.getType().getTypeOfElement()) {
            case STRING, CLASS, OBJECTREF -> "aload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL;
            case THIS -> "aload_0" + NL;
            default -> "iload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL;
        };
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        // TODO: Hardcoded to int return type, needs to be expanded

        if (returnInst.getOperand() != null) {
            code.append(generators.apply(returnInst.getOperand()));
        }

        if (returnInst.getReturnType().toString().equals("INT32") || returnInst.getReturnType().toString().equals("BOOLEAN")) {
            code.append("ireturn").append(NL);
        } else {
            code.append("return").append(NL);
        }

        return code.toString();
    }

    private String generateCall(CallInstruction callInst) {
        var code = new StringBuilder();

        if (callInst.getInvocationType().toString().equals("NEW")) {
            code.append("new ");
            // TODO - not exactly sure how to proceed from here, needs work
            for (var element : callInst.getOperands()) {
                if (element.getType().getTypeOfElement() != ElementType.OBJECTREF) {
                    throw new NotImplementedException(element.getType().getTypeOfElement());
                }

                // TODO: VERY HACKY - review asap
                code.append(((Operand) element).getName()).append(NL);
            }
            code.append("dup").append(NL);
        } else if (callInst.getInvocationType().toString().equals("invokespecial")) {
            code.append(invokeSpecial(callInst));
        } else if (callInst.getInvocationType().toString().equals("invokestatic")) {
            code.append(invokeStatic(callInst));
        } else if (callInst.getInvocationType().toString().equals("invokevirtual")) {
            code.append(invokeVirtual(callInst));
        } else {
            throw new NotImplementedException(callInst.getInvocationType());
        }

        return code.toString();
    }

    private String invokeSpecial(CallInstruction callInst) {
        var code = new StringBuilder();

        code.append(generators.apply(callInst.getOperands().get(0)));
        code.append("invokespecial ").append(ollirResult.getOllirClass().getClassName()).append("/<init>");
        code.append("(");

        for (Element el : callInst.getArguments()) {
            switch (el.getType().getTypeOfElement()) {
                case INT32 -> code.append("I");
                case BOOLEAN -> code.append("B");
                case VOID -> code.append("V");
                case STRING -> code.append("Ljava/lang/String;");
            }
        }

        code.append(")").append(this.getType(callInst.getReturnType())).append(NL);

        if (callInst.getReturnType().getTypeOfElement() == ElementType.VOID) {
            code.append("pop").append(NL);
        }

        return code.toString();
    }

    private String invokeStatic(CallInstruction callInst) {
        var code = new StringBuilder();

        var callerName = ((Operand) callInst.getOperands().get(0)).getName();
        var methodName = ((LiteralElement) callInst.getMethodName()).getLiteral().replace("\"", "");

        boolean checkImport = currentMethod.getOllirClass().getImports().stream().anyMatch(
                imported -> imported.equals(callerName) || imported.equals(methodName)
        );

        boolean checkSuper = currentMethod.getOllirClass().getSuperClass() != null && currentMethod.getOllirClass().getSuperClass().equals(callerName);

        if (!(checkImport || checkSuper)) {
            // Load caller
            code.append(generators.apply(callInst.getOperands().get(0)));
        }

        // remove first two operands, since they are the caller and the method name
        for (var element : callInst.getOperands().subList(2, callInst.getOperands().size())) {
            code.append(generators.apply(element));
        }

        code.append("invokestatic ").append(callerName).append("/").append(methodName).append("(");

        for (Element element : callInst.getArguments()) {
            code.append(this.getType(element.getType()));
        }

        code.append(")").append(this.getType(callInst.getReturnType())).append(NL);
        return code.toString();
    }

    private String invokeVirtual(CallInstruction callInst) {
        var code = new StringBuilder();

        // Load all operands (TODO: not sure about this code)
        // Load the caller
        code.append(generators.apply(callInst.getOperands().get(0)));

        // remove first two operands, since they are the caller and the method name
        for (var element : callInst.getOperands().subList(2, callInst.getOperands().size())) {
            code.append(generators.apply(element));
        }

        var callerName = ((Operand) callInst.getOperands().get(0)).getName().equals("this") ?
                ollirResult.getOllirClass().getClassName() :
                ((ClassType) callInst.getOperands().get(0).getType()).getName();
        var methodName = ((LiteralElement) callInst.getMethodName()).getLiteral().replace("\"", "");
        code.append("invokevirtual ").append(callerName).append("/").append(methodName).append("(");

        for (Element element : callInst.getArguments()) {
            code.append(this.getType(element.getType()));
        }

        code.append(")").append(this.getType(callInst.getReturnType())).append(NL);

        //to try without pop
        /*
        if (callInst.getReturnType().getTypeOfElement() == ElementType.VOID) {
            code.append("pop").append(NL);
        }
        */

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putField) {
        var code = new StringBuilder();

        Element first = putField.getOperands().get(0);
        Element second = putField.getOperands().get(1);
        Element third = putField.getOperands().get(2);

        code.append(generators.apply(first));
        code.append(generators.apply(third));
        code.append("putfield ").append(ollirResult.getOllirClass().getClassName()).append("/")
                .append(((Operand) second).getName()).append(" ")
                .append(this.getType(second.getType())).append(NL);

        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();

        Element first = getField.getOperands().get(0);
        Element second = getField.getOperands().get(1);

        code.append(generators.apply(first));
        code.append("getfield ").append(ollirResult.getOllirClass().getClassName()).append("/")
                .append(((Operand) second).getName()).append(" ")
                .append(this.getType(second.getType())).append(NL);

        return code.toString();
    }

    // -----------------------------------------------------------------
    // Utils (maybe move to a class of its own)
    // -----------------------------------------------------------------

    /**
     * Returns the Jasmin type for a given Ollir type.
     * @param type Ollir type
     * @return Jasmin type
     */
    private String getType(Type type) {
        switch (type.getTypeOfElement()) {
            case INT32:
                return "I";
            case BOOLEAN:
                return "Z";
            case VOID:
                return "V";
            case STRING:
                return "Ljava/lang/String;";
            case CLASS, THIS, OBJECTREF:
                var className = ((ClassType) type).getName();
                if (className.equals("this")) {
                    return "L" + ollirResult.getOllirClass().getClassName() + ";";
                }

                return "L" + className + ";";
            default:
                throw new NotImplementedException(type.getTypeOfElement());
        }
    }

    /**
     * Stores the value of an operand in the stack.
     * @param op Operand
     * @return Jasmin code
     */
    private String store(Operand op) {
        return switch (op.getType().getTypeOfElement()) {
            case STRING, CLASS, OBJECTREF -> "astore " + currentMethod.getVarTable().get(op.getName()).getVirtualReg() + NL;
            case THIS -> "astore_0" + NL;
            default -> "istore " + currentMethod.getVarTable().get(op.getName()).getVirtualReg() + NL;
        };
    }
}
