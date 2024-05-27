package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;

import static pt.up.fe.comp2024.ast.Kind.ARRAY_TYPE;
import static pt.up.fe.comp2024.ast.Kind.VARARGS_TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {

        Kind.checkIfType(typeNode);

        String typeName;
        boolean isArray = Kind.check(typeNode, ARRAY_TYPE);
        if (isArray) {
            typeName = typeNode.getJmmChild(0).get("name");
        } else {
            typeName = typeNode.get("name");
        }

        return toOllirType(typeName, Kind.check(typeNode, VARARGS_TYPE) || isArray);
    }

    public static String toOllirType(Type type) {
        return toOllirType(type.getName(), type.isArray());
    }

    private static String toOllirType(String typeName, boolean isArray) {

        return "." + switch (typeName) {
            case "int" -> isArray ? "array.i32" : "i32";
            case "boolean" -> isArray ? "array.bool" : "bool";
            default -> typeName; // Class name (TODO: check for arrays)
        };
    }


}
