package top.niunaijun.obfuscator;

import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.stmt.*;
import top.niunaijun.obfuscator.chain.FlowObfuscator;
import top.niunaijun.obfuscator.chain.IfObfuscator;
import top.niunaijun.obfuscator.chain.SubObfuscator;
import top.niunaijun.obfuscator.chain.SemanticObfuscator;
import top.niunaijun.obfuscator.chain.RuntimeObfuscator;
import top.niunaijun.obfuscator.chain.ErrorSemanticObfuscator;
import top.niunaijun.obfuscator.chain.LicenseObfuscator;
import top.niunaijun.obfuscator.chain.base.ObfuscatorChain;

import java.util.*;

public class IRObfuscator {

	private static IRObfuscator obfuscator;
	private final ObfuscatorConfiguration configuration;
	private final List<ObfuscatorChain> chains;

	public static IRObfuscator get(ObfuscatorConfiguration obf) {
		if (obfuscator == null) {
			synchronized (IRObfuscator.class) {
				if (obfuscator == null) {
					obfuscator = new IRObfuscator(obf);
				}
			}
		}
		return obfuscator;
	}

	public IRObfuscator(ObfuscatorConfiguration configuration) {
		this.configuration = configuration;
		this.chains = new LinkedList<>();
		// 只保留 FlowObfuscator 进行单独测试
        this.chains.add(new FlowObfuscator(configuration));
        this.chains.add(new SubObfuscator(configuration));
        this.chains.add(new IfObfuscator(configuration));

		// 暂时注释其他混淆器
		// this.chains.add(new RuntimeObfuscator(configuration));
		// this.chains.add(new LicenseObfuscator(configuration));
		// this.chains.add(new SemanticObfuscator(configuration));
		// this.chains.add(new ErrorSemanticObfuscator(configuration));
	}

	public void reBuildInstructions(IrMethod ir) {
		if (configuration == null)
			return;
		if (!configuration.accept(ir.owner, ir.name)) {
			return;
		}

		for (ObfuscatorChain chain : chains) {
			for (int i = 0; i < configuration.getObfDepth(); i++) {
				List<Stmt> newStmts = new ArrayList<>();
				List<Stmt> origStmts = new ArrayList<>();
				for (Stmt value : ir.stmts) {
					origStmts.add(value);
				}
				RebuildIfResult rebuildIfResult;
				for (Stmt stmt : ir.stmts) {
					if (chain.canHandle(ir, stmt)) {
						rebuildIfResult = chain.reBuild(ir, stmt, origStmts);
						if (rebuildIfResult != null) {
							newStmts.addAll(rebuildIfResult.getResult());
						}
					} else {
						newStmts.add(stmt);
					}
				}
				chain.reBuildEnd(ir, newStmts, origStmts);
				ir.stmts.clear();
				ir.stmts.addAll(newStmts);
				if (!chain.canDepth()) {
					break;
				}
			}
		}
	}
}
