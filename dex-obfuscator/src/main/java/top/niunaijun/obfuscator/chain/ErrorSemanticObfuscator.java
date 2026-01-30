package top.niunaijun.obfuscator.chain;

import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.expr.*;
import com.googlecode.dex2jar.ir.stmt.*;
import top.niunaijun.obfuscator.LBlock;
import top.niunaijun.obfuscator.ObfuscatorConfiguration;
import top.niunaijun.obfuscator.RebuildIfResult;
import top.niunaijun.obfuscator.chain.base.BaseObfuscatorChain;

import java.util.*;

/**
 * 可信错误语义混淆器
 * 添加非 crash 的错误执行路径，确保错误路径看起来像正常业务逻辑
 */
public class ErrorSemanticObfuscator extends BaseObfuscatorChain {

    public ErrorSemanticObfuscator(ObfuscatorConfiguration obfuscatorConfiguration) {
        super(obfuscatorConfiguration);
    }

    @Override
    public boolean canDepth() {
        return true;
    }

    @Override
    public boolean canHandle(IrMethod ir, Stmt stmt) {
        // 只处理计算和返回指令
        return stmt.st == Stmt.ST.ASSIGN || 
               stmt.st == Stmt.ST.RETURN || 
               stmt.st == Stmt.ST.VOID_INVOKE;
    }

    @Override
    public RebuildIfResult reBuild0(IrMethod ir, Stmt stmt, List<Stmt> origStmts) {
        // 为计算添加错误语义
        if (stmt.st == Stmt.ST.ASSIGN) {
            Stmt.E2Stmt e2Stmt = (Stmt.E2Stmt) stmt;
            Value op1 = e2Stmt.getOp1();
            Value op2 = e2Stmt.getOp2();
            
            // 只处理局部变量赋值
            if (op1.vt == Value.VT.LOCAL) {
                List<Stmt> newStmts = new ArrayList<>();
                
                // 添加错误语义路径
                newStmts.addAll(generateErrorSemanticPath((Local) op1, op2));
                
                // 保留原始赋值作为备用
                newStmts.add(stmt);
                
                return new RebuildIfResult(newStmts);
            }
        }
        // 为返回指令添加错误语义
        else if (stmt.st == Stmt.ST.RETURN) {
            Stmt.E1Stmt returnStmt = (Stmt.E1Stmt) stmt;
            Value returnValue = returnStmt.getOp();
            
            if (returnValue != null) {
                List<Stmt> newStmts = new ArrayList<>();
                
                // 添加返回值的错误语义
                newStmts.addAll(generateReturnValueErrorSemantic(returnValue));
                
                // 保留原始返回
                newStmts.add(stmt);
                
                return new RebuildIfResult(newStmts);
            }
        }
        return null;
    }

    @Override
    public void reBuildEnd(IrMethod ir, List<Stmt> newStmts, List<Stmt> origStmts) {
        // 在方法末尾添加错误语义触发条件
        if (!newStmts.isEmpty()) {
            // 找到方法末尾
            int lastStmtIndex = newStmts.size() - 1;
            Stmt lastStmt = newStmts.get(lastStmtIndex);
            
            if (lastStmt.st == Stmt.ST.RETURN) {
                // 在返回前添加错误语义触发条件
                List<Stmt> errorTrigger = generateErrorTriggerCondition();
                newStmts.addAll(lastStmtIndex, errorTrigger);
            }
        }
    }

    /**
     * 生成错误语义路径
     */
    private List<Stmt> generateErrorSemanticPath(Local targetLocal, Value originalExpr) {
        List<Stmt> stmts = new ArrayList<>();
        
        // 生成错误触发条件
        Local errorTrigger = newLocal("error_trigger", "Z");
        stmts.add(Stmts.nAssign(errorTrigger, Exprs.nLt(
                Exprs.nInvokeStatic(new Value[0], "Ljava/lang/Math;", "random", new String[]{}, "D"),
                Exprs.nDouble(0.1), // 10% 概率触发错误
                "Z"
        )));
        
        // 错误路径标签
        LabelStmt errorPathLabel = createLabel();
        LabelStmt normalPathLabel = createLabel();
        LabelStmt endLabel = createLabel();
        
        // 条件跳转
        stmts.add(Stmts.nIf(errorTrigger, errorPathLabel));
        stmts.add(Stmts.nGoto(normalPathLabel));
        
        // 错误路径
        stmts.add(errorPathLabel);
        
        // 根据表达式类型生成不同的错误语义
        String valueType = originalExpr.valueType;
        if (valueType == null) {
            // 类型为 null，返回默认值
            stmts.add(Stmts.nAssign(targetLocal, Exprs.nNull()));
        } else if (valueType.equals("I")) {
            // 整数计算错误：精度下降
            stmts.add(Stmts.nAssign(targetLocal, Exprs.nInt(new Random().nextInt(100))));
        } else if (valueType.equals("D")) {
            // 浮点数计算错误：精度下降
            stmts.add(Stmts.nAssign(targetLocal, Exprs.nDouble(new Random().nextDouble() * 100)));
        } else if (valueType.equals("Ljava/lang/String;")) {
            // 字符串错误：顺序变化
            stmts.add(Stmts.nAssign(targetLocal, Exprs.nString("")));
        } else if (valueType.equals("Z")) {
                // 布尔值错误：随机结果
                stmts.add(Stmts.nAssign(targetLocal, Exprs.nConstant(new Random().nextBoolean())));
        } else {
            // 其他类型：返回默认值
            stmts.add(Stmts.nAssign(targetLocal, getDefaultValue(valueType)));
        }
        
        stmts.add(Stmts.nGoto(endLabel));
        
        // 正常路径（空操作）
        stmts.add(normalPathLabel);
        stmts.add(Stmts.nNop());
        stmts.add(Stmts.nGoto(endLabel));
        
        // 结束标签
        stmts.add(endLabel);
        
        return stmts;
    }

    /**
     * 生成返回值错误语义
     */
    private List<Stmt> generateReturnValueErrorSemantic(Value returnValue) {
        List<Stmt> stmts = new ArrayList<>();
        
        // 生成错误触发条件
        Local errorTrigger = newLocal("return_error_trigger", "Z");
        stmts.add(Stmts.nAssign(errorTrigger, Exprs.nLt(
                Exprs.nInvokeStatic(new Value[0], "Ljava/lang/Math;", "random", new String[]{}, "D"),
                Exprs.nDouble(0.05), // 5% 概率触发错误
                "Z"
        )));
        
        // 错误路径标签
        LabelStmt errorPathLabel = createLabel();
        LabelStmt normalPathLabel = createLabel();
        LabelStmt endLabel = createLabel();
        
        // 条件跳转
        stmts.add(Stmts.nIf(errorTrigger, errorPathLabel));
        stmts.add(Stmts.nGoto(normalPathLabel));
        
        // 错误路径：修改返回值
        stmts.add(errorPathLabel);
        
        // 根据返回值类型生成错误语义
        if (returnValue.valueType.equals("I")) {
            // 整数返回错误
            stmts.add(Stmts.nAssign(returnValue, Exprs.nInt(new Random().nextInt(100))));
        } else if (returnValue.valueType.equals("D")) {
            // 浮点数返回错误
            stmts.add(Stmts.nAssign(returnValue, Exprs.nDouble(new Random().nextDouble() * 100)));
        } else if (returnValue.valueType.equals("Ljava/lang/String;")) {
            // 字符串返回错误
            stmts.add(Stmts.nAssign(returnValue, Exprs.nString("")));
        } else if (returnValue.valueType.equals("Z")) {
            // 布尔值返回错误
            stmts.add(Stmts.nAssign(returnValue, Exprs.nConstant(new Random().nextBoolean())));
        }
        
        stmts.add(Stmts.nGoto(endLabel));
        
        // 正常路径
        stmts.add(normalPathLabel);
        stmts.add(Stmts.nNop());
        stmts.add(Stmts.nGoto(endLabel));
        
        // 结束标签
        stmts.add(endLabel);
        
        return stmts;
    }

    /**
     * 生成错误触发条件
     */
    private List<Stmt> generateErrorTriggerCondition() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 生成基于环境状态的错误触发条件
        Local errorTrigger = newLocal("final_error_trigger", "Z");
        
        // 基于时间戳的触发条件
        stmts.add(Stmts.nAssign(errorTrigger, Exprs.nLt(
                Exprs.nRem(
                        Exprs.nConstant(1),
                        Exprs.nInt(100),
                        "I"
                ),
                Exprs.nInt(5), // 5% 概率
                "Z"
        )));
        
        // 基于线程 ID 的触发条件
        stmts.add(Stmts.nAssign(errorTrigger, Exprs.nOr(
                errorTrigger,
                Exprs.nLt(
                        Exprs.nRem(
                                Exprs.nConstant(1),
                                Exprs.nInt(100),
                                "I"
                        ),
                        Exprs.nInt(5),
                        "Z"
                ),
                "Z"
        )));
        
        return stmts;
    }

    /**
     * 获取类型的默认值
     */
    private Value getDefaultValue(String type) {
        switch (type) {
            case "I":
                return Exprs.nInt(0);
            case "S":
                return Exprs.nShort((short) 0);
            case "J":
                return Exprs.nLong(0);
            case "F":
                return Exprs.nFloat(0);
            case "D":
                return Exprs.nDouble(0);
            case "C":
                return Exprs.nChar((char) 0);
            case "B":
                return Exprs.nByte((byte) 0);
            case "Z":
                return Exprs.nConstant(false);
            default:
                return Exprs.nNull();
        }
    }

    /**
     * 创建一个新的标签
     */
    private LabelStmt createLabel() {
        return new LabelStmt();
    }
}
