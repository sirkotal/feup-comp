package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.up.fe.comp2024.ast.Kind.METHOD_DECL;
import static pt.up.fe.comp2024.ast.Kind.PARAM;
import static pt.up.fe.comp2024.ast.Kind.VAR_DECL;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        int index = 0;
        ArrayList<String> imports = new ArrayList<>();

        while (Kind.IMPORT_DECL.check(root.getJmmChild(index))) {
            // 1 to len - 1 to remove []
            String import_string = root.getJmmChild(index).get("value")
                    .substring(1, root.getJmmChild(index).get("value").length() - 1);

            import_string = String.join(".", import_string.split(", "));
            imports.add(import_string);
            index++;
        }

        var classDecl = root.getJmmChild(index);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        // TODO: not sure about `null`
        String superName = classDecl.hasAttribute("extended") ? classDecl.get("extended") : null;

        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(className, superName, imports, fields, methods, returnTypes, params, locals);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> {
                    String returnName;
                    boolean isArray;

                    if (method.get("name").equals("main")) {
                        returnName = "void";
                        isArray = false;
                    } else {
                        var returnType = method.getJmmChild(0);
                        isArray = returnType.getObject("isArray", Boolean.class);

                        if (isArray) {
                            returnName = returnType.getJmmChild(0).get("name");
                        } else {
                            returnName = returnType.get("name");
                        }
                    }

                    map.put(method.get("name"), new Type(
                            returnName,
                            isArray
                    ));
                });

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), method.getChildren(PARAM).stream()
                        .map(param -> new Symbol(
                                new Type(param.getJmmChild(0).get("name"), param.getJmmChild(0).getObject("isArray", Boolean.class)),
                                param.get("name")
                        )).toList()
                ));

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> {
                    boolean isArray = varDecl.getJmmChild(0).getObject("isArray", Boolean.class);
                    String typeName;

                    if (isArray) {
                        typeName = varDecl.getJmmChild(0).getJmmChild(0).get("name");
                    } else {
                        typeName = varDecl.getJmmChild(0).get("name");
                    }

                    return new Symbol(new Type(typeName, isArray), varDecl.get("name"));
                }).toList();
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        return classDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> {
                    boolean isArray = varDecl.getJmmChild(0).getObject("isArray", Boolean.class);
                    String typeName;

                    if (isArray) {
                        typeName = varDecl.getJmmChild(0).getJmmChild(0).get("name");
                    } else {
                        typeName = varDecl.getJmmChild(0).get("name");
                    }

                    return new Symbol(new Type(typeName, isArray), varDecl.get("name"));
                }).toList();
    }

}
