/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.expr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ExpressionEvaluator {
    private final Map<String, Expression> variables = new HashMap<>();

    private final ExpressionParser parser = new ExpressionParser();

    public void putVariable(String name, String expression) {
        variables.put(name, parser.parseExpression(expression));
    }

    public Object evaluate(String expression) {
        return doEvaluate(parser.parseExpression(expression));
    }

    protected Object doEvaluate(Expression expression) {
        if (expression instanceof Literal literal) {
            return literal.value();
        }
        if (expression instanceof Addition add) {
            return plus(doEvaluate(add.left()), doEvaluate(add.right()));
        }
        if (expression instanceof Substraction sub) {
            return minus(doEvaluate(sub.left()), doEvaluate(sub.right()));
        }
        if (expression instanceof Multiplication mul) {
            return mul(doEvaluate(mul.left()), doEvaluate(mul.right()));
        }
        if (expression instanceof Division div) {
            return div(doEvaluate(div.left()), doEvaluate(div.right()));
        }
        if (expression instanceof Variable v) {
            var name = v.name();
            var e = variables.get(name);
            if (e != null) {
                return doEvaluate(e);
            }
            return variable(v.name());
        }
        if (expression instanceof FunctionCall fun) {
            return functionCall(fun.function(), fun.operands().stream().map(this::doEvaluate).toList());
        }
        throw new UnsupportedOperationException("Unexpected expression type " + expression);
    }

    protected abstract Object plus(Object left, Object right);
    protected abstract Object minus(Object left, Object right);
    protected abstract Object mul(Object left, Object right);
    protected abstract Object div(Object left, Object right);
    protected Object variable(String name) {
        throw new IllegalStateException("Undefined variable '" + name + "'");
    }

    protected abstract Object functionCall(BuiltinFunction function, List<Object> arguments);

}
