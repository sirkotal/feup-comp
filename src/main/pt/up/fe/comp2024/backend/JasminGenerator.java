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
import java.util.Objects;
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

    private int binaryLabelCounter = 0;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(Instruction.class, this::generateInstruction);
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

    private String generateInstruction(Instruction inst) {
        StringBuilder code = new StringBuilder();

        for (var label : currentMethod.getLabels().entrySet()) {
            if (label.getValue().equals(inst)) {
                code.append(label.getKey()).append(":").append(NL);
            }
        }

        code.append(switch (inst.getInstType()) {
            case ASSIGN -> generateAssign((AssignInstruction) inst);
            case NOPER -> {
                if (inst instanceof SingleOpInstruction singleOp) {
                    yield generateSingleOp(singleOp);
                } else {
                    throw new NotImplementedException("TODO: " + inst.getClass());
                }
            }
            case BINARYOPER -> generateBinaryOp((BinaryOpInstruction) inst);
            case RETURN -> generateReturn((ReturnInstruction) inst);
            case CALL -> generateCall((CallInstruction) inst);
            case PUTFIELD -> generatePutField((PutFieldInstruction) inst);
            case GETFIELD -> generateGetField((GetFieldInstruction) inst);
            case UNARYOPER -> generateUnaryOp((UnaryOpInstruction) inst);
            case BRANCH -> generateCondBranch((CondBranchInstruction) inst);
            case GOTO -> generateGoto((GotoInstruction) inst);
        });

        return code.toString();
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

            for (var field : classUnit.getFields()) {
                code.append(".field public ").append(field.getFieldName()).append(" ").append(this.getType(field.getFieldType())).append(NL);
            }

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

            for (var field : classUnit.getFields()) {
                code.append(".field public ").append(field.getFieldName()).append(" ").append(this.getType(field.getFieldType())).append(NL);
            }

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

        // Add limits
        int loc_count = currentMethod.getVarTable().size();

        if (!currentMethod.getVarTable().keySet().contains("this") && !currentMethod.isStaticMethod()) {
            loc_count += 1;
        }
        //System.out.println(currentMethod.getVarTable());
        for (Descriptor v: currentMethod.getVarTable().values()) {
            if (v.getScope().toString().equals("FIELD")) {
                loc_count -= 1;
            }
        }

        int stack_size = 0;
        int stack_count = 0;

        for (Instruction instruction : method.getInstructions()) {
            if (instruction instanceof AssignInstruction) {
                var rhs_inst = ((AssignInstruction) instruction).getRhs();
                if (rhs_inst instanceof CallInstruction) {
                    CallInstruction callInst = (CallInstruction) rhs_inst;
                    stack_count += callInst.getArguments().size() + 1;
                }
                else if (rhs_inst instanceof BinaryOpInstruction) {
                    stack_count += 2;
                }
                else if (rhs_inst instanceof UnaryOpInstruction) {
                    stack_count += 1;
                }
            }
            else if (instruction instanceof CallInstruction) {
                CallInstruction callInst = (CallInstruction) instruction;
                stack_count += callInst.getArguments().size() + 1;
            }
            else if (instruction instanceof GetFieldInstruction || instruction instanceof PutFieldInstruction) {
                stack_count += 1;
            }
            else if (instruction instanceof ReturnInstruction) { // not sure about this one
                stack_count += 1;
            }

            stack_size = Math.max(stack_size, stack_count);

            if (instruction instanceof BinaryOpInstruction) { // gets 2 values from the stack and pushes one, so the count actually goes down
                stack_count -= 1;
            }
            else if (instruction instanceof UnaryOpInstruction || instruction instanceof ReturnInstruction) { // just pops from the stack I believe (maybe UnaryOp doesn't, not entirely sure)
                stack_count -= 1;
            }
        }

        code.append(TAB).append(".limit stack ").append(98).append(NL);
        code.append(TAB).append(".limit locals ").append(loc_count).append(NL);

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

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var rhs = generators.apply(assign.getRhs());
        if (rhs.endsWith("pop" + NL)) {
            rhs = rhs.substring(0, rhs.length() - 4);
        }

        if (lhs instanceof ArrayOperand) {
            code.append(this.store(operand));
            code.append(rhs);
            code.append("iastore").append(NL);
        } else {
            code.append(rhs);
            code.append(this.store(operand));
        }

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    // TODO: Check this out, it might be wrong but previous version is a no-go for sure
    private String generateOperand(Operand operand) {
        // Same as `this.store()` but for loading values
        return switch (operand.getType().getTypeOfElement()) {
            case STRING, OBJECTREF, ARRAYREF -> "aload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL;
            case THIS -> "aload_0" + NL;
            case INT32, BOOLEAN -> {
                if (operand instanceof ArrayOperand) {
                    yield "aload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL +
                            generators.apply(((ArrayOperand) operand).getIndexOperands().get(0)) +
                            "iaload" + NL;
                } else {
                    yield "iload " + currentMethod.getVarTable().get(operand.getName()).getVirtualReg() + NL;
                }
            }
            default -> "";
        };
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        String lhs = generators.apply(binaryOp.getLeftOperand());
        String rhs = generators.apply(binaryOp.getRightOperand());

        // apply operation
        var opType = binaryOp.getOperation().getOpType();

        switch (opType) {
            case ADD, MUL, DIV, SUB, LTH -> code.append(lhs).append(rhs);
            case GTE -> code.append(rhs).append(lhs);
        }

        var op = switch (opType) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            case LTH, GTE -> {
                String label = binaryOp.getOperation().getOpType() == OperationType.LTH ? "cmp_lt_" : "cmp_ge_";

                String opCode = "isub" + NL
                        + "iflt " + label + binaryLabelCounter + "_true" + NL
                        + "iconst_0" + NL
                        + "goto " + label + binaryLabelCounter + "_end" + NL
                        + label + binaryLabelCounter + "_true:" + NL
                        + "iconst_1" + NL
                        + label + binaryLabelCounter + "_end:" + NL;

                this.binaryLabelCounter++;
                yield opCode;
            }
            case AND -> throw new RuntimeException("Code should not reach here");
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
            if (callInst.getReturnType().getTypeOfElement() == ElementType.OBJECTREF) {
                code.append("new ");
                // TODO - not exactly sure how to proceed from here, needs work
                for (var element : callInst.getOperands()) {
                    code.append(((Operand) element).getName()).append(NL);
                }
                code.append("dup").append(NL);
            } else if (callInst.getReturnType().getTypeOfElement() == ElementType.ARRAYREF) {
                code.append(generators.apply(callInst.getArguments().get(0)))
                        .append("newarray int").append(NL);
            } else {
                throw new NotImplementedException(callInst.getReturnType().getTypeOfElement());
            }
        } else if (callInst.getInvocationType().toString().equals("invokespecial")) {
            code.append(invokeSpecial(callInst));
        } else if (callInst.getInvocationType().toString().equals("invokestatic")) {
            code.append(invokeStatic(callInst));
        } else if (callInst.getInvocationType().toString().equals("invokevirtual")) {
            code.append(invokeVirtual(callInst));
        } else if (callInst.getInvocationType().toString().equals("arraylength")) {
            code.append(arrayLength(callInst));
        } else {
            throw new NotImplementedException(callInst.getInvocationType());
        }

        if (callInst.getReturnType().getTypeOfElement() != ElementType.VOID) {
            code.append("pop").append(NL);
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

        return code.toString();
    }

    private String arrayLength(CallInstruction callInst) {
        var code = new StringBuilder();

        code.append(generators.apply(callInst.getOperands().get(0)));
        code.append("arraylength").append(NL);

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

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        // load value
        code.append(generators.apply(unaryOp.getOperand()));

        // apply operation
        if (Objects.requireNonNull(unaryOp.getOperation().getOpType()) == OperationType.NOTB) {
            code.append("iconst_1").append(NL).append("ixor").append(NL);
        } else {
            throw new NotImplementedException(unaryOp.getOperation().getOpType());
        }

        return code.toString();
    }

    private String generateCondBranch(CondBranchInstruction codeBranch) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(codeBranch.getCondition()))
                .append("ifne ").append(codeBranch.getLabel()).append(NL);

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + NL;
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
            case ARRAYREF:
                return "[I";
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
            case STRING, CLASS, OBJECTREF, ARRAYREF -> "astore " + currentMethod.getVarTable().get(op.getName()).getVirtualReg() + NL;
            case THIS -> "astore_0" + NL;
            default -> {
                if (op instanceof ArrayOperand arrayOperand) {
                    yield "aload " + currentMethod.getVarTable().get(op.getName()).getVirtualReg() + NL +
                            generators.apply(((ArrayOperand) op).getIndexOperands().get(0));
                }

                yield "istore " + currentMethod.getVarTable().get(op.getName()).getVirtualReg() + NL;
            }
        };
    }
}
