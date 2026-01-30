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
 * License / 计费深度绑定混淆器
 * 将 license 状态作为语义选择输入，而不是判断条件
 */
public class LicenseObfuscator extends BaseObfuscatorChain {

    public LicenseObfuscator(ObfuscatorConfiguration obfuscatorConfiguration) {
        super(obfuscatorConfiguration);
    }

    @Override
    public boolean canDepth() {
        return true;
    }

    @Override
    public boolean canHandle(IrMethod ir, Stmt stmt) {
        // 只处理方法开头和控制流指令
        return stmt.st == Stmt.ST.LABEL || 
               stmt.st == Stmt.ST.GOTO || 
               stmt.st == Stmt.ST.IF;
    }

    @Override
    public RebuildIfResult reBuild0(IrMethod ir, Stmt stmt, List<Stmt> origStmts) {
        // 在方法开头添加 license 状态采集
        if (stmt.st == Stmt.ST.LABEL && isMethodEntry(stmt, origStmts)) {
            List<Stmt> newStmts = new ArrayList<>();
            newStmts.add(stmt); // 保留原标签
            
            // 添加 license 状态采集
            newStmts.addAll(generateLicenseStatusCollection());
            
            // 将 license 状态融入语义选择
            newStmts.addAll(generateLicenseSemanticBinding());
            
            return new RebuildIfResult(newStmts);
        }
        return null;
    }

    @Override
    public void reBuildEnd(IrMethod ir, List<Stmt> newStmts, List<Stmt> origStmts) {
        // 在方法末尾添加 license 状态重置
        if (!newStmts.isEmpty()) {
            for (int i = newStmts.size() - 1; i >= 0; i--) {
                Stmt endStmt = newStmts.get(i);
                if (endStmt.st == Stmt.ST.RETURN) {
                    // 在返回指令前添加 license 状态重置
                    List<Stmt> resetStmts = generateLicenseStatusReset();
                    newStmts.addAll(i, resetStmts);
                    break;
                }
            }
        }
    }

    /**
     * 检查是否为方法入口标签
     */
    private boolean isMethodEntry(Stmt stmt, List<Stmt> origStmts) {
        return origStmts.indexOf(stmt) == 0;
    }

    /**
     * 生成 license 状态采集代码
     */
    private List<Stmt> generateLicenseStatusCollection() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 模拟 license 状态采集（实际项目中应替换为真实的 license 检查）
        Local licenseStatus = newLocal("license_status", "I");
        
        // 生成随机的 license 状态（1-3 表示不同的 license 级别）
        stmts.add(Stmts.nAssign(licenseStatus, Exprs.nCast(
                Exprs.nInvokeStatic(new Value[0], "Ljava/lang/Math;", "random", new String[]{}, "D"),
                "D", "I"
        )));
        stmts.add(Stmts.nAssign(licenseStatus, Exprs.nRem(
                licenseStatus,
                Exprs.nInt(3),
                "I"
        )));
        stmts.add(Stmts.nAssign(licenseStatus, Exprs.nAdd(
                licenseStatus,
                Exprs.nInt(1),
                "I"
        )));
        
        // 将 license 状态存储到局部变量中，供后续使用
        stmts.add(Stmts.nAssign(newLocal("license_status_final", "I"), licenseStatus));
        
        return stmts;
    }

    /**
     * 生成 license 语义绑定代码
     */
    private List<Stmt> generateLicenseSemanticBinding() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 获取 license 状态
        Local licenseStatus = newLocal("license_status", "I");
        
        // 生成基于 license 状态的语义选择器
        Local semanticSelector = newLocal("semantic_selector", "I");
        
        // 将 license 状态融入语义选择
        stmts.add(Stmts.nAssign(semanticSelector, Exprs.nXor(
                licenseStatus,
                Exprs.nCast(Exprs.nInvokeStatic(new Value[0], "Ljava/lang/System;", "currentTimeMillis", new String[]{}, "J"), "J", "I"),
                "I"
        )));
        
        // 添加 license 状态对实现集合的影响
        stmts.add(Stmts.nAssign(semanticSelector, Exprs.nMul(
                semanticSelector,
                licenseStatus,
                "I"
        )));
        
        // 将语义选择器存储为局部变量
        stmts.add(Stmts.nAssign(newLocal("semantic_selector_final", "I"), semanticSelector));
        
        return stmts;
    }

    /**
     * 生成 license 状态重置代码
     */
    private List<Stmt> generateLicenseStatusReset() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 重置 license 相关变量
        stmts.add(Stmts.nAssign(newLocal("license_reset", "Z"), Exprs.nConstant(true)));
        
        return stmts;
    }

    /**
     * 为核心方法添加 license 绑定的实现选择
     */
    private List<Stmt> generateLicenseBasedImplementationSelection() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 获取 license 状态和语义选择器
        Local licenseStatus = newLocal("license_status", "I");
        Local semanticSelector = newLocal("semantic_selector", "I");
        
        // 生成基于 license 的实现选择
        LabelStmt impl1Label = createLabel();
        LabelStmt impl2Label = createLabel();
        LabelStmt impl3Label = createLabel();
        LabelStmt endLabel = createLabel();
        
        // 基于 license 状态的条件跳转
        stmts.add(Stmts.nIf(Exprs.nEq(licenseStatus, Exprs.nInt(1), "Z"), impl1Label));
        stmts.add(Stmts.nIf(Exprs.nEq(licenseStatus, Exprs.nInt(2), "Z"), impl2Label));
        stmts.add(Stmts.nGoto(impl3Label));
        
        // License 级别 1：基础实现
        stmts.add(impl1Label);
        stmts.add(Stmts.nAssign(newLocal("impl_level", "I"), Exprs.nInt(1)));
        stmts.add(Stmts.nGoto(endLabel));
        
        // License 级别 2：增强实现
        stmts.add(impl2Label);
        stmts.add(Stmts.nAssign(newLocal("impl_level", "I"), Exprs.nInt(2)));
        stmts.add(Stmts.nGoto(endLabel));
        
        // License 级别 3：完整实现
        stmts.add(impl3Label);
        stmts.add(Stmts.nAssign(newLocal("impl_level", "I"), Exprs.nInt(3)));
        stmts.add(Stmts.nGoto(endLabel));
        
        // 结束标签
        stmts.add(endLabel);
        
        return stmts;
    }

    /**
     * 创建一个新的标签
     */
    private LabelStmt createLabel() {
        return new LabelStmt();
    }
}
