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
 * 语义多实现混淆器
 * 为核心功能生成多个等价但结构不同的实现
 */
public class SemanticObfuscator extends BaseObfuscatorChain {

    public SemanticObfuscator(ObfuscatorConfiguration obfuscatorConfiguration) {
        super(obfuscatorConfiguration);
    }

    @Override
    public boolean canDepth() {
        return true;
    }

    @Override
    public boolean canHandle(IrMethod ir, Stmt stmt) {
        // 只处理核心方法，如包含计算、逻辑判断的方法
        return stmt.st == Stmt.ST.ASSIGN || 
               stmt.st == Stmt.ST.IDENTITY || 
               stmt.st == Stmt.ST.VOID_INVOKE || 
               stmt.st == Stmt.ST.IF;
    }

    @Override
    public RebuildIfResult reBuild0(IrMethod ir, Stmt stmt, List<Stmt> origStmts) {
        // 为核心计算生成多个实现
        if (stmt.st == Stmt.ST.ASSIGN) {
            Stmt.E2Stmt e2Stmt = (Stmt.E2Stmt) stmt;
            Value op1 = e2Stmt.getOp1();
            Value op2 = e2Stmt.getOp2();
            
            // 只处理局部变量赋值
            if (op1.vt == Value.VT.LOCAL) {
                Local local = (Local) op1;
                
                // 检查是否为计算表达式
                if (isCalculationExpr(op2)) {
                    List<Stmt> newStmts = new ArrayList<>();
                    
                    // 生成多个实现
                    List<List<Stmt>> implementations = generateMultipleImplementations(local, op2);
                    
                    // 随机选择一个实现
                    int implIndex = new Random().nextInt(implementations.size());
                    List<Stmt> selectedImpl = implementations.get(implIndex);
                    
                    newStmts.addAll(selectedImpl);
                    
                    return new RebuildIfResult(newStmts);
                }
            }
        }
        return null;
    }

    @Override
    public void reBuildEnd(IrMethod ir, List<Stmt> newStmts, List<Stmt> origStmts) {
        // 在方法末尾添加实现选择逻辑
        if (newStmts.size() > 0) {
            // 生成实现选择器
            Local implSelector = newLocal("impl_selector", "I");
            newStmts.add(Stmts.nAssign(implSelector, Exprs.nInvokeStatic(new Value[0], "Ljava/lang/Math", "random", new String[]{}, "D")));
            newStmts.add(Stmts.nAssign(implSelector, Exprs.nMul(implSelector, Exprs.nDouble(3), "D"))); // 使用固定值 3
            newStmts.add(Stmts.nAssign(implSelector, Exprs.nCast(implSelector, "D", "I")));
            
            // 添加实现选择标记
            newStmts.add(Stmts.nNop());
        }
    }

    /**
     * 检查是否为计算表达式
     */
    private boolean isCalculationExpr(Value expr) {
        return expr instanceof BinopExpr || 
               expr instanceof UnopExpr || 
               expr.vt == Value.VT.INVOKE_STATIC || 
               expr.vt == Value.VT.INVOKE_VIRTUAL;
    }

    /**
     * 生成多个等价的实现
     */
    private List<List<Stmt>> generateMultipleImplementations(Local targetLocal, Value originalExpr) {
        List<List<Stmt>> implementations = new ArrayList<>();
        
        // 实现 1: 原始实现
        List<Stmt> impl1 = new ArrayList<>();
        impl1.add(Stmts.nAssign(targetLocal, originalExpr));
        implementations.add(impl1);
        
        // 实现 2: 等价变换实现
        List<Stmt> impl2 = generateEquivalentImplementation(targetLocal, originalExpr);
        if (impl2 != null && !impl2.isEmpty()) {
            implementations.add(impl2);
        }
        
        // 实现 3: 冗余计算实现
        List<Stmt> impl3 = generateRedundantImplementation(targetLocal, originalExpr);
        if (impl3 != null && !impl3.isEmpty()) {
            implementations.add(impl3);
        }
        
        // 确保至少有 3 个实现
        while (implementations.size() < 3) {
            List<Stmt> fallbackImpl = new ArrayList<>();
            fallbackImpl.add(Stmts.nAssign(targetLocal, originalExpr));
            implementations.add(fallbackImpl);
        }
        
        return implementations;
    }

    /**
     * 生成等价变换实现
     */
    private List<Stmt> generateEquivalentImplementation(Local targetLocal, Value originalExpr) {
        List<Stmt> impl = new ArrayList<>();
        
        // 简化实现，直接使用原始表达式
        impl.add(Stmts.nAssign(targetLocal, originalExpr));
        
        return impl;
    }

    /**
     * 生成冗余计算实现
     */
    private List<Stmt> generateRedundantImplementation(Local targetLocal, Value originalExpr) {
        List<Stmt> impl = new ArrayList<>();
        
        // 创建临时变量
        Local tempLocal1 = newLocal("temp_redundant1", originalExpr.valueType);
        
        // 添加冗余计算
        impl.add(Stmts.nAssign(tempLocal1, originalExpr));
        impl.add(Stmts.nAssign(targetLocal, tempLocal1));
        
        return impl;
    }

    /**
     * 为逻辑判断生成多个实现
     */
    private List<Stmt> generateMultipleLogicImplementations(Stmt ifStmt) {
        List<Stmt> implementations = new ArrayList<>();
        
        // 简化实现，直接添加原始条件
        implementations.add(ifStmt);
        
        return implementations;
    }
}
