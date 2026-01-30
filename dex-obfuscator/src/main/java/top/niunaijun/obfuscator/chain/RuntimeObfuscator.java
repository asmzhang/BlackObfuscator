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
 * Runtime 决策钩子混淆器
 * 采集环境信号并生成状态向量，用于选择 build-time 已存在的路径
 */
public class RuntimeObfuscator extends BaseObfuscatorChain {

    public RuntimeObfuscator(ObfuscatorConfiguration obfuscatorConfiguration) {
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
        // 在方法开头添加环境信号采集
        if (stmt.st == Stmt.ST.LABEL && isMethodEntry(stmt, origStmts)) {
            List<Stmt> newStmts = new ArrayList<>();
            newStmts.add(stmt); // 保留原标签
            
            // 添加环境信号采集
            newStmts.addAll(generateEnvironmentSignalCollection());
            
            // 生成状态向量
            newStmts.addAll(generateStateVector());
            
            return new RebuildIfResult(newStmts);
        }
        return null;
    }

    @Override
    public void reBuildEnd(IrMethod ir, List<Stmt> newStmts, List<Stmt> origStmts) {
        // 在方法末尾添加状态重置逻辑
        if (!newStmts.isEmpty()) {
            // 找到方法末尾的返回指令
            for (int i = newStmts.size() - 1; i >= 0; i--) {
                Stmt endStmt = newStmts.get(i);
                if (endStmt.st == Stmt.ST.RETURN) {
                    // 在返回指令前添加状态重置
                    List<Stmt> resetStmts = generateStateReset();
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
        // 方法入口标签通常是第一个语句
        return origStmts.indexOf(stmt) == 0;
    }

    /**
     * 生成环境信号采集代码
     */
    private List<Stmt> generateEnvironmentSignalCollection() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 采集当前时间戳
        Local timeStamp = newLocal("env_timestamp", "J");
        stmts.add(Stmts.nAssign(timeStamp, Exprs.nInvokeStatic(new Value[0], "Ljava/lang/System;", "currentTimeMillis", new String[]{}, "J")));
        
        // 采集线程 ID
        Local threadId = newLocal("env_thread_id", "J");
        stmts.add(Stmts.nAssign(threadId, Exprs.nInvokeVirtual(
                new Value[]{Exprs.nInvokeStatic(new Value[0], "Ljava/lang/Thread;", "currentThread", new String[]{}, "Ljava/lang/Thread;")},
                "java/lang/Thread", "getId", new String[]{}, "J"
        )));
        
        // 采集调用栈深度
        Local stackDepth = newLocal("env_stack_depth", "I");
        Local stackTrace = newLocal("stack_trace", "[Ljava/lang/StackTraceElement;");
        stmts.add(Stmts.nAssign(stackTrace, Exprs.nInvokeVirtual(
                new Value[]{Exprs.nInvokeStatic(new Value[0], "Ljava/lang/Thread;", "currentThread", new String[]{}, "Ljava/lang/Thread;")},
                "java/lang/Thread", "getStackTrace", new String[]{}, "[Ljava/lang/StackTraceElement;"
        )));
        stmts.add(Stmts.nAssign(stackDepth, Exprs.nLength(stackTrace)));
        
        // 采集内存使用情况
        Local memoryUsage = newLocal("env_memory_usage", "J");
        Local runtime = newLocal("runtime", "Ljava/lang/Runtime;");
        stmts.add(Stmts.nAssign(runtime, Exprs.nInvokeStatic(
                new Value[0], "java/lang/Runtime", "getRuntime", new String[]{}, "Ljava/lang/Runtime;"
        )));
        stmts.add(Stmts.nAssign(memoryUsage, Exprs.nInvokeVirtual(
                new Value[]{runtime}, "java/lang/Runtime", "totalMemory", new String[]{}, "J"
        )));
        
        return stmts;
    }

    /**
     * 生成状态向量
     */
    private List<Stmt> generateStateVector() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 生成随机种子
        Local seed = newLocal("state_seed", "J");
        stmts.add(Stmts.nAssign(seed, Exprs.nInvokeStatic(new Value[0], "Ljava/lang/System", "currentTimeMillis", new String[]{}, "J")));
        
        // 生成状态向量
        Local stateVector = newLocal("state_vector", "I");
        stmts.add(Stmts.nAssign(stateVector, Exprs.nCast(seed, "J", "I")));
        
        // 混入更多环境信号
        stmts.add(Stmts.nAssign(stateVector, Exprs.nXor(
                stateVector,
                Exprs.nCast(Exprs.nInvokeStatic(new Value[0], "Ljava/lang/Math", "random", new String[]{}, "D"), "D", "I"),
                "I"
        )));
        
        // 添加时间源不一致性检测
        Local timeInconsistency = newLocal("time_inconsistency", "I");
        stmts.add(Stmts.nAssign(timeInconsistency, Exprs.nCast(
                Exprs.nInvokeStatic(new Value[0], "Ljava/lang/System", "nanoTime", new String[]{}, "J"),
                "J", "I"
        )));
        stmts.add(Stmts.nAssign(stateVector, Exprs.nXor(stateVector, timeInconsistency, "I")));
        
        return stmts;
    }

    /**
     * 生成状态重置代码
     */
    private List<Stmt> generateStateReset() {
        List<Stmt> stmts = new ArrayList<>();
        
        // 重置状态变量（局部变量会自动销毁，这里主要是做一些清理工作）
        Local resetFlag = newLocal("reset_flag", "Z");
        stmts.add(Stmts.nAssign(resetFlag, Exprs.nConstant(true)));
        
        return stmts;
    }

    /**
     * 为控制流添加状态依赖
     */
    private List<Stmt> addStateDependencyToControlFlow(Stmt ifStmt) {
        List<Stmt> stmts = new ArrayList<>();
        
        if (ifStmt.st == Stmt.ST.IF) {
            Stmt.E1Stmt ifs = (Stmt.E1Stmt) ifStmt;
            Value condition = ifs.getOp();
            LabelStmt target = null;
            
            // 获取目标标签（这里需要根据实际的 IfStmt 实现来调整）
            // 暂时跳过获取 target 的逻辑，直接使用原始的 if 语句
            
            // 获取状态向量
            Local stateVector = newLocal("state_vector", "I");
            stmts.add(Stmts.nAssign(stateVector, Exprs.nCast(
                    Exprs.nInvokeStatic(new Value[0], "Ljava/lang/System", "currentTimeMillis", new String[]{}, "J"),
                    "J", "I"
            )));
            
            // 将状态向量混入条件
            Value newCondition = Exprs.nAnd(
                    condition,
                    Exprs.nGt(stateVector, Exprs.nInt(0), "Z"),
                    "Z"
            );
            
            // 暂时不创建新的 if 语句，避免目标标签的问题
        }
        
        return stmts;
    }
}
