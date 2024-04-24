package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

public class InvalidConditions extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.IF_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
    }

    private Void visitIfStmt(JmmNode ifStmt, SymbolTable table) {
        var condition = ifStmt.getJmmChild(0);
        var conditionType = TypeUtils.getExprType(condition, table);

        if (!conditionType.getName().equals(TypeUtils.getBooleanTypeName())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(condition),
                    NodeUtils.getColumn(condition),
                    "Invalid condition type for if statement.",
                    null
            ));
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        var condition = whileStmt.getJmmChild(0);
        var conditionType = TypeUtils.getExprType(condition, table);

        if (!conditionType.getName().equals(TypeUtils.getBooleanTypeName())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(condition),
                    NodeUtils.getColumn(condition),
                    "Invalid condition type for while statement.",
                    null
            ));
        }

        return null;
    }
}
