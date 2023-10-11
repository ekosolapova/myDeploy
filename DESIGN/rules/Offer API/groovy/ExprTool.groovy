package org.bool.expr

import com.bpodgursky.jbool_expressions.*
import com.bpodgursky.jbool_expressions.rules.RuleSet
import org.apache.commons.lang3.tuple.Triple
import org.openl.binding.IBoundNode
import org.openl.binding.impl.*
import org.openl.binding.impl.operator.Comparison
import org.openl.rules.dt.AST
import org.openl.rules.vm.SimpleRulesRuntimeEnv
import org.openl.types.IOpenClass
import org.openl.types.IOpenMethod
import org.openl.types.java.JavaOpenClass
import org.openl.vm.SimpleVM

import java.util.function.Supplier
import java.util.stream.Collectors

class ExprTool {

    static Formula[] split(AST ast) {
        if (ast.getBoundNode() == null) {
            return new Formula[0]
        }
        var expression = parse(ast, ast.getBoundNode())
        var dnf = RuleSet.toDNF(expression)
        List<Formula> expressions = new ArrayList<>()
        if (dnf instanceof Or) {
            var or = (Or<Node>) dnf
            for (Expression<Node> e : or.expressions) {
                var formula = new Formula(exprToSE(ast, e, false).value, exprToSE(ast, e, true).value)
                expressions.add(formula)
            }
        } else {
            expressions.add(new Formula(exprToSE(ast, dnf, false).value, exprToSE(ast, dnf, true).value))
        }
        return expressions.stream().sorted(Comparator.comparing(Formula::getFormula)).toArray(Formula[]::new)
    }

    private static String removeExtraSpaces(String s) {
        var sb = new StringBuilder()
        char prev = ' ' as char
        boolean skip = false
        for (char c : s.chars()) {
            if (c == '"' as char) {
                skip = !skip
            }
            if (!skip) {
                if (c != ' ' as char || prev != ' ' as char) {
                    sb.append(c)
                }
            } else {
                sb.append(c)
            }
            prev = c
        }
        return sb.toString().trim()
    }

    private static boolean isBooleanType(IOpenClass openClass) {
        return openClass == JavaOpenClass.getOpenClass(Boolean.class) || openClass == JavaOpenClass.BOOLEAN
    }

    private static String getOppositeOperation(String op) {
        switch (op) {
            case ">": return "<"
            case "<": return ">"
            case ">=": return "<="
            case "<=": return ">="
            case "==": return "=="
            case "!=": return "!="
            case "string==": return "string=="
            case "string!=": return "string!="
            case "string>": return "string<"
            case "string<": return "string>"
            case "string>=": return "string<="
            case "string<=": return "string>="
        }
        return null
    }

    private static String getOperation(IOpenMethod openMethod) {
        String name = openMethod.getName()
        switch (name) {
            case "gt": return ">"
            case "lt": return "<"
            case "ge": return ">="
            case "le": return "<="
            case "eq": return "=="
            case "ne": return "!="
            case "string_eq": return "string=="
            case "string_ne": return "string!="
            case "string_gt": return "string>"
            case "string_lt": return "string<"
            case "string_ge": return "string>="
            case "string_le": return "string<="
        }
        return null
    }

    private static Triple<Boolean, String, String> canSimplify(String op, IBoundNode boundNode) {
        if (("<=" == op || ">=" == op) && isLiteral(boundNode)) {
            if (boundNode.getType() == JavaOpenClass.BYTE || boundNode.getType() == JavaOpenClass.getOpenClass(Byte.class)) {
                Byte v = (Byte) getLiteralValue(boundNode)
                boolean f = "<=" == op && v != Byte.MAX_VALUE || ">=" == op && v != Byte.MIN_VALUE
                return Triple.of(f, String.valueOf("<=" == op ? v + 1 : v - 1), "<=" == op ? "<" : ">")
            } else if (boundNode.getType() == JavaOpenClass.SHORT || boundNode.getType() == JavaOpenClass.getOpenClass(Short.class)) {
                Short v = (Short) getLiteralValue(boundNode)
                boolean f = "<=" == op && v != Short.MAX_VALUE || ">=" == op && v != Short.MIN_VALUE
                return Triple.of(f, String.valueOf("<=" == op ? v + 1 : v - 1), "<=" == op ? "<" : ">")
            } else if (boundNode.getType() == JavaOpenClass.INT || boundNode.getType() == JavaOpenClass.getOpenClass(Integer.class)) {
                Integer v = (Integer) getLiteralValue(boundNode)
                boolean f = "<=" == op && v != Integer.MAX_VALUE || ">=" == op && v != Integer.MIN_VALUE
                return Triple.of(f, String.valueOf("<=" == op ? v + 1 : v - 1), "<=" == op ? "<" : ">")
            } else if (boundNode.getType() == JavaOpenClass.LONG || boundNode.getType() == JavaOpenClass.getOpenClass(Long.class)) {
                Long v = (Long) getLiteralValue(boundNode)
                boolean f = "<=" == op && v != Long.MAX_VALUE || ">=" == op && v != Long.MIN_VALUE
                return Triple.of(f, String.valueOf("<=" == op ? v + 1 : v - 1), "<=" == op ? "<" : ">")
            } else if (boundNode.getType() == JavaOpenClass.getOpenClass(BigInteger.class)) {
                BigInteger v = (BigInteger) getLiteralValue(boundNode)
                return Triple.of(Boolean.TRUE, String.valueOf("<=" == op ? v.add(BigInteger.ONE) : v.add(BigInteger.ONE.negate())), "<=" == op ? "<" : ">")
            }
        }
        return Triple.of(Boolean.FALSE, null, null)
    }

    private static String fieldBoundNodeToString(AST ast, FieldBoundNode fieldBoundNode) {
        String s = null
        IBoundNode b = fieldBoundNode
        boolean f = false
        while (b instanceof FieldBoundNode || b instanceof IndexNode) {
            if (b instanceof FieldBoundNode) {
                FieldBoundNode fieldBoundNode1 = (FieldBoundNode) b
                s = (s == null) ? fieldBoundNode1.getBoundField().getName() : fieldBoundNode1.getBoundField().getName() + (f ? "." : "") + s
                f = true
            } else if (b instanceof IndexNode) {
                IBoundNode indexBoundNode = ((IndexNode) b).getChildren()[0]
                String d = "[" + boundNodeToSE(ast, indexBoundNode, true).value + "]"
                s = (s == null) ? d : d + (f ? "." : "") + s
                f = false
            }
            b = b.getTargetNode()
        }
        return b == null ? s : removeExtraSpaces(ast.getCode(fieldBoundNode))
    }

    private static int getOperationPriority(String op) {
        //https://en.cppreference.com/w/c/language/operator_precedence
        if (op == "not") {
            return 2
        }
        if (op == ">" || op == ">=" || op == "<" || op == "<=" || op == "string<" || op == "string<=" || op == "string>" || op == "string>=") {
            return 6
        }
        if (op == "==" || op == "!=" || op == "string!=" || op == "string==") {
            return 7
        }
        if (op == "and") {
            return 11
        }
        if (op == "or") {
            return 12
        }
        if (op == "?" || op == "?:") {
            return 13
        }
        throw new IllegalStateException()
    }

    private static SE boundNodeToSE(AST ast, IBoundNode boundNode, boolean formula) {
        if (formula) {
            if (boundNode instanceof BinaryOpNode) {
                BinaryOpNode binaryOpNode = (BinaryOpNode) boundNode
                if (isBooleanType(binaryOpNode.getType())) {
                    IOpenMethod method = binaryOpNode.getMethodCaller().getMethod()
                    if (method.getDeclaringClass() == JavaOpenClass.getOpenClass(Comparison.class) && method.getSignature().getNumberOfParameters() == 2) {
                        Expression<Node> e0 = parse(ast, binaryOpNode.children[0])
                        Expression<Node> e1 = parse(ast, binaryOpNode.children[1])
                        String op = getOperation(binaryOpNode.getMethodCaller().getMethod())
                        if (e0 instanceof Literal && e1 instanceof Literal && (op == "==" || op == "!=")) {
                            Literal literal0 = (Literal) e0
                            Literal literal1 = (Literal) e1
                            Object v = method.invoke(null, new Object[]{literal0.getValue(),
                                    literal1.getValue()}, new SimpleVM.SimpleRuntimeEnv())
                            return new SE(String.valueOf(v), true, null)
                        }
                        if (e0 instanceof Literal && !(e1 instanceof Literal) && (op == "==" || op == "!=")) {
                            if (op == "==") {
                                return exprToSE(ast, e1, formula)
                            } else {
                                return exprToSE(ast, RuleSet.simplify(Not.of(e1)), formula)
                            }
                        }
                        if (!(e0 instanceof Literal) && e1 instanceof Literal && (op == "==" || op == "!=")) {
                            if (op == "==") {
                                return exprToSE(ast, e1, formula)
                            } else {
                                return exprToSE(ast, RuleSet.simplify(Not.of(e0)), formula)
                            }
                        }
                        boolean isLiteral0 = e0 instanceof Literal || e0 instanceof Variable && isLiteral(((Variable<Node>) e0).value.boundNode)
                        boolean isLiteral1 = e1 instanceof Literal || e1 instanceof Variable && isLiteral(((Variable<Node>) e1).value.boundNode)
                        if (isLiteral0 && !isLiteral1 && op != null) {
                            String op1 = getOppositeOperation(op)
                            Triple<Boolean, String, String> d = canSimplify(op1, binaryOpNode.getChildren()[0])
                            if (d.getLeft()) {
                                SE se = exprToSE(ast, e1, formula)
                                return new SE(se.value + " " + d.getRight() + " " + d.getMiddle(), se.determined, d.getRight())
                            }
                        }
                        if (!isLiteral0 && isLiteral1 && op != null) {
                            Triple<Boolean, String, String> d = canSimplify(op, binaryOpNode.getChildren()[1])
                            if (d.getLeft()) {
                                SE se = exprToSE(ast, e0, formula)
                                return new SE(se.value + " " + d.getRight() + " " + d.getMiddle(), se.determined, d.getRight())
                            }
                        }
                        if ((op == "!=" || op == "==") && e0 instanceof Variable && e1 instanceof Variable && e0 == e1) {
                            var v0 = ((Variable<Node>) e0).value.boundNode
                            var v1 = ((Variable<Node>) e1).value.boundNode
                            if (v0 instanceof FieldBoundNode && v1 instanceof FieldBoundNode) {
                                return exprToSE(ast, Literal.of(op == "=="), formula)
                            }
                        }
                        if (op != null) {
                            if ((op == "==" || op == "!=") && e0 instanceof Not<Node> && e1 instanceof Not<Node>) {
                                e0 = RuleSet.simplify(Not.of(e0))
                                e1 = RuleSet.simplify(Not.of(e1))
                                isLiteral0 = e0 instanceof Literal || e0 instanceof Variable && isLiteral(((Variable<Node>) e0).value.boundNode)
                                isLiteral1 = e1 instanceof Literal || e1 instanceof Variable && isLiteral(((Variable<Node>) e1).value.boundNode)
                            } else if (e0 instanceof Not<Node> || e1 instanceof Not<Node>) {
                                if (e0 instanceof Not<Node>) {
                                    e0 = RuleSet.simplify(Not.of(e0))
                                    isLiteral0 = e0 instanceof Literal || e0 instanceof Variable && isLiteral(((Variable<Node>) e0).value.boundNode)
                                } else {
                                    e1 = RuleSet.simplify(Not.of(e1))
                                    isLiteral1 = e1 instanceof Literal || e1 instanceof Variable && isLiteral(((Variable<Node>) e1).value.boundNode)
                                }
                                op = op == "==" ? "!=" : "=="
                            }
                            SE se1 = exprToSE(ast, e0, formula)
                            SE se2 = exprToSE(ast, e1, formula)

                            String p1 = se1.value
                            String p2 = se2.value
                            if (op == "==" || op == "!=") {
                                if (se1.determined && se2.determined && p1 == p2) {
                                    return exprToSE(ast, Literal.of(op == "=="), formula)
                                }
                                boolean l1 = p1 == "true" || p1 == "false"
                                boolean l2 = p2 == "true" || p2 == "false"
                                if (l1 && l2) {
                                    if (p1 == p2) {
                                        return exprToSE(ast, Literal.of(op == "=="), formula)
                                    } else {
                                        return exprToSE(ast, Literal.of(op == "!="), formula)
                                    }
                                }
                                if (l1 && !l2) {
                                    return p1 == "true" ? se2 : exprToSE(ast, RuleSet.simplify(Not.of(e1)), formula)
                                }
                                if (!l1 && l2) {
                                    return p2 == "true" ? se1 : exprToSE(ast, RuleSet.simplify(Not.of(e0)), formula)
                                }
                            }
                            if (p1 < p2 && !(isLiteral0 && !isLiteral1) || !isLiteral0 && isLiteral1) {
                                int dOp = getOperationPriority(op)
                                String e = ((se1.operation != null && dOp < getOperationPriority(se1.operation)) ? "(" + p1 + ")" : p1) + " " + op + " " + ((se2.operation != null && dOp <= getOperationPriority(se2.operation)) ? "(" + p2 + ")" : p2)
                                return new SE(e, se1.determined && se2.determined, op)
                            } else {
                                String op1 = getOppositeOperation(op)
                                if (op1 != null) {
                                    int dOp = getOperationPriority(op)
                                    String e = ((se2.operation != null && dOp < getOperationPriority(se2.operation)) ? "(" + p2 + ")" : p2) + " " + op1 + " " + ((se1.operation != null && dOp <= getOperationPriority(se1.operation)) ? "(" + p1 + ")" : p1)
                                    return new SE(e, se1.determined && se2.determined, op1)
                                }
                            }
                        }
                    }
                }
            } else if (boundNode instanceof FieldBoundNode) {
                return new SE(fieldBoundNodeToString(ast, (FieldBoundNode) boundNode), true, null)
            } else if (boundNode instanceof IfNode) {
                throw new IllegalStateException()
            } else if (boundNode instanceof TypeCastNode) {
                //The cast is not valuable in strings to compare
                return boundNodeToSE(ast, boundNode.getChildren()[0], formula)
            } else if (boundNode instanceof MethodBoundNode) {
                MethodBoundNode methodBoundNode = (MethodBoundNode) boundNode
                StringBuilder sb = new StringBuilder()
                sb.append(methodBoundNode.getMethodCaller().getMethod().getName()).append("(")
                boolean f = false
                for (IBoundNode boundNode1 : methodBoundNode.getChildren()) {
                    if (f) {
                        sb.append(", ")
                    }
                    f = true
                    sb.append(boundNodeToSE(ast, boundNode1, formula).value)
                }
                sb.append(")")
                return new SE(sb.toString(), false, null)
            }
        }
        if (boundNode.getChildren().length == 1 && isIgnorableBoundNode(boundNode)) {
            return boundNodeToSE(ast, boundNode.getChildren()[0], formula)
        }
        return new SE(formula ? removeExtraSpaces(ast.getCode(boundNode)) : ast.getCode(boundNode), false, null)
    }

    private static SE exprToSE(AST ast, Expression<Node> expression, boolean formula) {
        if (expression instanceof And) {
            var and = (And<Node>) expression
            List<String> expressions = new ArrayList<>()
            boolean f = true
            for (Expression<Node> e : and.expressions) {
                SE se = exprToSE(ast, e, formula)
                expressions.add(se.value)
                f = f && se.determined
            }
            Collections.sort(expressions)
            return new SE(expressions.stream().collect(Collectors.joining(" and ")), f, "and")
        } else if (expression instanceof Or) {
            var or = (Or<Node>) expression
            List<String> expressions = new ArrayList<>()
            boolean f = true
            for (Expression<Node> e : or.expressions) {
                SE se = exprToSE(ast, e, formula)
                expressions.add(se.value)
                f = f && se.determined
            }
            Collections.sort(expressions)
            return new SE(expressions.stream().collect(Collectors.joining(" or ")), f, "or")
        } else if (expression instanceof Variable) {
            var variable = (Variable<Node>) expression
            if ((formula || variable.getValue().supplier != null) && variable.getValue().getV() instanceof SE) {
                if (variable.getValue().supplier != null) {
                    SE se = (SE) variable.getValue().getV()
                    return new SE(variable.getValue().supplier.get(), se.determined, se.operation)
                } else {
                    return (SE) variable.getValue().getV()
                }
            } else {
                return boundNodeToSE(ast, variable.getValue().boundNode, formula)
            }
        } else if (expression instanceof Literal) {
            return new SE(String.valueOf(((Literal) expression).getValue()), true, null)
        } else if (expression instanceof Not<Node>) {
            Expression e = RuleSet.simplify(Not.of(expression))
            SE se = exprToSE(ast, e, true)
            SE se1 = !formula ? exprToSE(ast, e, formula) : se
            if (se.value == "true" || se.value == "false") {
                return new SE(se.value == "true" ? exprToSE(ast, Literal.of(false), true).value : exprToSE(ast, Literal.of(true), true).value, true, null)
            }
            boolean w = se.operation != null && getOperationPriority(se.operation) > getOperationPriority("not")
            return new SE("not " + (w ? "(" : "") + se1.value + (w ? ")" : ""), se.determined, "not")
        }
        throw new IllegalStateException()
    }

    private static Expression<Node> parse(AST ast, IBoundNode boundNode) {
        if (boundNode instanceof BinaryOpNodeAnd) {
            var binaryOpNodeAnd = (BinaryOpNodeAnd) boundNode
            var parseLeft = parse(ast, binaryOpNodeAnd.getLeft())
            var parseRight = parse(ast, binaryOpNodeAnd.getRight())
            List<Expression<Node>> children = new ArrayList<>()
            if (parseLeft instanceof And) {
                children.addAll(Arrays.asList(((And<Node>) parseLeft).expressions))
            } else {
                children.add(parseLeft)
            }
            if (parseRight instanceof And) {
                children.addAll(Arrays.asList(((And<Node>) parseRight).expressions))
            } else {
                children.add(parseRight)
            }
            return And.of(children)
        } else if (boundNode instanceof BinaryOpNodeOr) {
            var binaryOpNodeOr = (BinaryOpNodeOr) boundNode
            var parseLeft = parse(ast, binaryOpNodeOr.getLeft())
            var parseRight = parse(ast, binaryOpNodeOr.getRight())
            List<Expression<Node>> children = new ArrayList<>()
            if (parseLeft instanceof Or) {
                children.addAll(Arrays.asList(((Or<Node>) parseLeft).expressions))
            } else {
                children.add(parseLeft)
            }
            if (parseRight instanceof Or) {
                children.addAll(Arrays.asList(((Or<Node>) parseRight).expressions))
            } else {
                children.add(parseRight)
            }
            return Or.of(children)
        } else if (isLiteral(boundNode)) {
            if (isBooleanType(boundNode.getType())) {
                if (Boolean.TRUE == getLiteralValue(boundNode)) {
                    return Literal.getTrue()
                } else if (Boolean.FALSE == getLiteralValue(boundNode)) {
                    return Literal.getFalse()
                }
            }
            return buildVariable(ast, boundNode)
        } else if (isNot(boundNode)) {
            return Not.of(parse(ast, boundNode.getChildren()[0]))
        } else if (boundNode instanceof BinaryOpNode) {
            var binaryOpNode = (BinaryOpNode) boundNode
            var method = binaryOpNode.getMethodCaller().getMethod()
            if (isBooleanType(binaryOpNode.getType())) {
                if (("eq" == method.getName() || "ne" == method.getName()) && method.getDeclaringClass() == JavaOpenClass.getOpenClass(Comparison.class) && method.getSignature().getNumberOfParameters() == 2 && isBooleanType(binaryOpNode.getChildren()[0].getType()) && isBooleanType(binaryOpNode.getChildren()[1].getType())) {
                    if (isLiteral(binaryOpNode.getChildren()[0])) {
                        var literal = binaryOpNode.getChildren()[0]
                        if (isBooleanType(literal.getType())) {
                            var node = parse(ast, binaryOpNode.getChildren()[1])
                            if ("eq" == method.getName()) {
                                return Boolean.TRUE == getLiteralValue(literal) ? node : Not.of(node)
                            } else {
                                return Boolean.TRUE == getLiteralValue(literal) ? Not.of(node) : node
                            }
                        }
                    }
                    if (isLiteral(binaryOpNode.getChildren()[1])) {
                        var literal = binaryOpNode.getChildren()[1]
                        if (isBooleanType(literal.getType())) {
                            var node = parse(ast, binaryOpNode.getChildren()[0])
                            if ("eq" == method.getName()) {
                                return Boolean.TRUE == getLiteralValue(literal) ? node : Not.of(node)
                            } else {
                                return Boolean.TRUE == getLiteralValue(literal) ? Not.of(node) : node
                            }
                        }
                    }
                }
                if (binaryOpNode.getMethodCaller().getMethod().getDeclaringClass() == JavaOpenClass.getOpenClass(Comparison.class) && binaryOpNode.getMethodCaller().getMethod().getSignature().getNumberOfParameters() == 2) {
                    boolean isLiteral0 = isLiteral(binaryOpNode.getChildren()[0])
                    boolean isLiteral1 = isLiteral(binaryOpNode.getChildren()[1])
                    if (isLiteral0 && isLiteral1) {
                        var literal1 = binaryOpNode.getChildren()[0]
                        var literal2 = binaryOpNode.getChildren()[1]
                        if (Boolean.TRUE == boundNode.getMethodCaller().invoke(null, new Object[]{getLiteralValue(literal1), getLiteralValue(literal2)}, new SimpleRulesRuntimeEnv())) {
                            return Literal.getTrue()
                        } else {
                            return Literal.getFalse()
                        }
                    }
                    var opName = binaryOpNode.getMethodCaller().getMethod().getName()
                    if ("ge" == opName || "le" == opName || "string_ge" == opName || "string_le" == opName) {
                        String op = null
                        String eqOp = null
                        String oppositeOp = null
                        if ("ge" == opName) {
                            op = ">"
                            oppositeOp = "<"
                            eqOp = "=="
                        } else if ("le" == opName) {
                            op = "<"
                            oppositeOp = ">"
                            eqOp = "=="
                        } else if ("string_ge" == opName) {
                            op = "string>"
                            oppositeOp = "string<"
                            eqOp = "string=="
                        } else if ("string_le" == opName) {
                            op = "string<"
                            oppositeOp = "string>"
                            eqOp = "string=="
                        }
                        var v0 = boundNodeToSE(ast, binaryOpNode.getChildren()[0], true)
                        var v1 = boundNodeToSE(ast, binaryOpNode.getChildren()[1], true)
                        if (!isLiteral1 && v0.value > v1.value || isLiteral0) {
                            SE t = v0
                            v0 = v1
                            v1 = t
                            op = oppositeOp
                        }
                        var opNode = new Node(boundNode, new SE(v0.value + " " + op + " " + v1.value, v0.determined && v1.determined, op))
                        opNode.supplier = () -> v0.value + " " + op + " " + v1.value
                        var eqNode = new Node(boundNode, new SE(v0.value + " " + eqOp + " " + v1.value, v0.determined && v1.determined, eqOp))
                        eqNode.supplier = () -> v0.value + " " + eqOp + " " + v1.value
                        return Or.of(Variable.of(eqNode), Variable.of(opNode))
                    }
                }
            }
            return buildVariable(ast, boundNode)
        } else if (boundNode.getChildren().length == 1 && isIgnorableBoundNode(boundNode)) {
            return parse(ast, boundNode.getChildren()[0])
        } else {
            return buildVariable(ast, boundNode)
        }
    }

    private static boolean isIgnorableBoundNode(IBoundNode boundNode) {
        return boundNode instanceof BlockNode || boundNode instanceof MethodCastNode
    }

    private static boolean isLiteral(IBoundNode boundNode) {
        if (boundNode instanceof LiteralBoundNode) {
            return true
        } else if (boundNode instanceof CastNode) {
            return isLiteral(boundNode.getChildren()[0])
        } else if (boundNode instanceof FieldBoundNode) {
            var fieldBoundNode = (FieldBoundNode) boundNode
            return fieldBoundNode.getBoundField().isStatic()
        }
        return false
    }

    private static Object getLiteralValue(IBoundNode boundNode) {
        if (boundNode instanceof LiteralBoundNode) {
            return ((LiteralBoundNode) boundNode).getValue()
        } else if (boundNode instanceof CastNode) {
            var castNode = (CastNode) boundNode
            return castNode.getCast().convert(getLiteralValue(castNode.getChildren()[0]))
        } else if (boundNode instanceof FieldBoundNode) {
            var fieldBoundNode = (FieldBoundNode) boundNode
            return fieldBoundNode.getBoundField().get(null, new SimpleRulesRuntimeEnv())
        }
        throw new IllegalStateException()
    }

    private static Expression<Node> buildVariable(AST ast, IBoundNode boundNode) {
        if (boundNode instanceof IfNode) {
            IfNode ifNode = (IfNode) boundNode
            if (isBooleanType(ifNode.getType())) {
                Expression<Node> condition = parse(ast, ifNode.getConditionNode())
                condition = RuleSet.simplify(condition)
                Expression<Node> then = parse(ast, ifNode.getThenNode())
                then = RuleSet.simplify(then)
                if (ifNode.getElseNode() == null) {
                    SE conditionSE = exprToSE(ast, condition, true)
                    if (then instanceof Not<Node>) {
                        Expression<Node> d = RuleSet.simplify(Not.of(then))
                        SE thenSE = exprToSE(ast, d, true)
                        SE se = new SE(conditionSE.value + " ? " + thenSE.value, conditionSE.determined && thenSE.determined, "?")
                        Node node = new Node(boundNode, se)
                        node.supplier = () -> exprToSE(ast, condition, false).value + " ? " + exprToSE(ast, d, false).value
                        return Not.of(Variable.of(node))
                    }
                    SE thenSE = exprToSE(ast, then, true)
                    SE se = new SE(conditionSE.value + " ? " + thenSE.value, conditionSE.determined && thenSE.determined, "?")
                    return Variable.of(new Node(boundNode, se))
                }
                Expression<Node> elseE = parse(ast, boundNode.getElseNode())
                elseE = RuleSet.simplify(elseE)
                boolean not = false
                if (then instanceof Not<Node> && elseE instanceof Not<Node>) {
                    then = RuleSet.simplify(Not.of(then))
                    elseE = RuleSet.simplify(Not.of(elseE))
                    not = true
                }
                boolean v = false
                if (condition instanceof Not<Node>) {
                    condition = RuleSet.simplify(Not.of(condition))
                    var t = elseE
                    elseE = then
                    then = t
                    v = true
                }
                SE conditionSE = exprToSE(ast, condition, true)
                SE thenSE = exprToSE(ast, then, true)
                SE elseSE = exprToSE(ast, elseE, true)
                String s = conditionSE.value + " ? " + thenSE.value + " : " + elseSE.value
                SE se = new SE(s, conditionSE.determined && thenSE.determined && elseSE.determined, "?:")
                Node node = new Node(boundNode, se)
                if (v || not) {
                    node.supplier = () -> exprToSE(ast, condition, false).value + " ? " + exprToSE(ast, then, false).value + " : " + exprToSE(ast, elseE, false).value
                }
                Variable<Node> variable = Variable.of(node)
                return not ? Not.of(variable) : variable
            }
        }
        return Variable.of(new Node(boundNode, boundNodeToSE(ast, boundNode, true)))
    }

    private static boolean isNot(IBoundNode boundNode) {
        if (boundNode instanceof UnaryOpNode) {
            var unaryOpNode = (UnaryOpNode) boundNode
            var openMethod = unaryOpNode.getMethodCaller().getMethod()
            return "not" == openMethod.getName() && openMethod.getDeclaringClass() == JavaOpenClass.getOpenClass(Operators.class)
        }
        return false
    }

    static class SE {
        String value
        boolean determined
        String operation

        SE(String value, boolean determined, String operation) {
            this.value = value
            this.determined = determined
            this.operation = operation
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (o == null || getClass() != o.class) return false

            SE se = (SE) o

            if (determined != se.determined) return false
            if (operation != se.operation) return false
            if (value != se.value) return false

            return true
        }

        int hashCode() {
            int result
            result = value.hashCode()
            result = 31 * result + (determined ? 1 : 0)
            result = 31 * result + (operation != null ? operation.hashCode() : 0)
            return result
        }
    }

    static class Node {
        IBoundNode boundNode
        Object v
        Supplier<String> supplier

        Node(boundNode, v) {
            this.boundNode = boundNode
            this.v = v
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (o == null || getClass() != o.class) return false
            var node = (Node) o
            return v == node.v
        }

        int hashCode() {
            return v != null ? v.hashCode() : 0
        }
    }

    static class Formula {
        String value
        String formula

        Formula(String value, String formula) {
            this.value = value
            this.formula = formula
        }

        String getValue() {
            return value
        }

        String getFormula() {
            return formula
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (o == null || getClass() != o.class) return false

            var that = (Formula) o

            if (formula != that.formula) return false

            return true
        }

        int hashCode() {
            return formula != null ? formula.hashCode() : 0
        }
    }
}