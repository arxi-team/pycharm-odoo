package dev.ngocta.pycharm.odoo.python.model;

import com.intellij.openapi.util.Computable;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import dev.ngocta.pycharm.odoo.OdooNames;
import dev.ngocta.pycharm.odoo.python.OdooPyUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class OdooFieldReferenceContributor extends PsiReferenceContributor {
    public static final PsiElementPattern.Capture<PyStringLiteralExpression> MODEL_INHERITS_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("inherits") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression,
                                       ProcessingContext context) {
                    PsiElement parent = pyStringLiteralExpression.getParent();
                    if (parent instanceof PyKeyValueExpression) {
                        if (pyStringLiteralExpression.equals(((PyKeyValueExpression) parent).getValue())) {
                            parent = parent.getParent();
                            if (OdooModelUtils.isInheritsAssignedValue(parent)) {
                                OdooModelClass modelClass = OdooModelUtils.getContainingOdooModelClass(pyStringLiteralExpression);
                                context.put(OdooFieldReferenceProvider.MODEL_CLASS, modelClass);
                                return true;
                            }
                            return false;
                        }
                    }
                    return false;
                }
            });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> MODEL_REC_NAME_PATTERN =
            psiElement(PyStringLiteralExpression.class).afterSiblingSkipping(
                    psiElement().withElementType(PyTokenTypes.EQ),
                    psiElement(PyTargetExpression.class).withName(OdooNames.MODEL_REC_NAME))
                    .with(new PatternCondition<PyStringLiteralExpression>("") {
                        @Override
                        public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression, ProcessingContext context) {
                            OdooModelClass modelClass = OdooModelUtils.getContainingOdooModelClass(pyStringLiteralExpression);
                            context.put(OdooFieldReferenceProvider.MODEL_CLASS, modelClass);
                            return true;
                        }
                    });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> MODEL_ORDER_PATTERN =
            psiElement(PyStringLiteralExpression.class).afterSiblingSkipping(
                    psiElement().withElementType(PyTokenTypes.EQ),
                    psiElement(PyTargetExpression.class).withName(OdooNames.MODEL_ORDER))
                    .with(new PatternCondition<PyStringLiteralExpression>("") {
                        @Override
                        public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression, ProcessingContext context) {
                            OdooModelClass modelClass = OdooModelUtils.getContainingOdooModelClass(pyStringLiteralExpression);
                            context.put(OdooFieldReferenceProvider.MODEL_CLASS, modelClass);
                            context.put(OdooFieldReferenceProvider.IS_SORT_ORDER, true);
                            return true;
                        }
                    });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> MAPPED_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("mapped") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression,
                                       ProcessingContext context) {
                    PsiElement parent = pyStringLiteralExpression.getParent();
                    if (parent instanceof PyArgumentList) {
                        parent = parent.getParent();
                        if (parent instanceof PyCallExpression) {
                            PyCallExpression callExpression = (PyCallExpression) parent;
                            PyExpression callee = callExpression.getCallee();
                            if (callee instanceof PyReferenceExpression) {
                                PyReferenceExpression referenceExpression = (PyReferenceExpression) callee;
                                if (OdooNames.MAPPED.equals(referenceExpression.getName())) {
                                    PyExpression qualifier = referenceExpression.getQualifier();
                                    if (qualifier != null) {
                                        context.put(OdooFieldReferenceProvider.ENABLE_SUB_FIELD, true);
                                        context.put(OdooFieldReferenceProvider.MODEL_CLASS_RESOLVER, () -> {
                                            PyType qualifierType = OdooPyUtils.getType(qualifier);
                                            OdooModelClassType modelClassType = OdooModelUtils.extractOdooModelClassType(qualifierType);
                                            return modelClassType != null ? modelClassType.getPyClass() : null;
                                        });
                                        return true;
                                    }
                                }
                            }
                            return false;
                        }
                    }
                    return false;
                }
            });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> DECORATOR_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("decorator") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression,
                                       ProcessingContext context) {
                    PsiElement parent = pyStringLiteralExpression.getParent();
                    if (parent instanceof PyArgumentList) {
                        parent = parent.getParent();
                        if (parent instanceof PyCallExpression) {
                            parent = parent.getParent();
                        }
                        if (parent instanceof PyDecorator) {
                            PyDecorator decorator = (PyDecorator) parent;
                            QualifiedName qualifiedName = decorator.getQualifiedName();
                            if (qualifiedName != null) {
                                String decoratorName = qualifiedName.toString();
                                if (OdooNames.API_DEPENDS.equals(decoratorName)
                                        || OdooNames.API_CONSTRAINS.equals(decoratorName)
                                        || OdooNames.API_ONCHANGE.equals(decoratorName)) {
                                    context.put(OdooFieldReferenceProvider.ENABLE_SUB_FIELD,
                                            OdooNames.API_DEPENDS.equals(decoratorName));
                                    OdooModelClass modelClass = OdooModelUtils.getContainingOdooModelClass(decorator);
                                    context.put(OdooFieldReferenceProvider.MODEL_CLASS, modelClass);
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }
            });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> ONE2MANY_INVERSE_NAME_PATTERN =
            OdooModelUtils.getFieldAttributePattern(1, OdooNames.FIELD_ATTR_INVERSE_NAME, OdooNames.FIELD_TYPE_ONE2MANY)
                    .with(new PatternCondition<PyStringLiteralExpression>("inverseName") {
                        @Override
                        public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression,
                                               ProcessingContext context) {
                            PyCallExpression callExpression = PsiTreeUtil.getParentOfType(pyStringLiteralExpression, PyCallExpression.class);
                            if (callExpression != null) {
                                PyStringLiteralExpression comodelExpression = callExpression.getArgument(
                                        0, OdooNames.FIELD_ATTR_COMODEL_NAME, PyStringLiteralExpression.class);
                                if (comodelExpression != null) {
                                    OdooModelClass modelClass = OdooModelClass.getInstance(comodelExpression.getStringValue(), callExpression.getProject());
                                    context.put(OdooFieldReferenceProvider.MODEL_CLASS, modelClass);
                                }
                            }
                            return true;
                        }
                    });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> RELATED_PATTERN =
            OdooModelUtils.getFieldAttributePattern(-1, OdooNames.FIELD_ATTR_RELATED)
                    .with(new PatternCondition<PyStringLiteralExpression>("related") {
                        @Override
                        public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression,
                                               ProcessingContext context) {
                            OdooModelClass modelClass = OdooModelUtils.getContainingOdooModelClass(pyStringLiteralExpression);
                            context.put(OdooFieldReferenceProvider.ENABLE_SUB_FIELD, true);
                            context.put(OdooFieldReferenceProvider.MODEL_CLASS, modelClass);
                            return true;
                        }
                    });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> CURRENCY_FIELD_PATTERN =
            OdooModelUtils.getFieldAttributePattern(-1, OdooNames.FIELD_ATTR_CURRENCY_FIELD, OdooNames.FIELD_TYPE_MONETARY)
                    .with(new PatternCondition<PyStringLiteralExpression>("currencyField") {
                        @Override
                        public boolean accepts(@NotNull PyStringLiteralExpression expression,
                                               ProcessingContext context) {
                            OdooModelClass modelClass = OdooModelUtils.getContainingOdooModelClass(expression);
                            context.put(OdooFieldReferenceProvider.MODEL_CLASS, modelClass);
                            return true;
                        }
                    });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> SEARCH_DOMAIN_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("searchDomain") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyReferenceExpression,
                                       ProcessingContext context) {
                    PyListLiteralExpression domainExpression = OdooModelUtils.getSearchDomainExpression(pyReferenceExpression, true);
                    if (domainExpression == null) {
                        return false;
                    }
                    Computable<OdooModelClass> modelClassResolver = OdooModelUtils.getSearchDomainContextResolver(domainExpression, true);
                    if (modelClassResolver != null || maybeContainFieldReferences(domainExpression)) {
                        context.put(OdooFieldReferenceProvider.MODEL_CLASS_RESOLVER, modelClassResolver);
                        context.put(OdooFieldReferenceProvider.ENABLE_SUB_FIELD, true);
                        return true;
                    }
                    return false;
                }
            });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> RECORD_VALUE_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("createValue") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression,
                                       ProcessingContext context) {
                    PsiElement valueExpression = OdooModelUtils.getRecordValueExpression(pyStringLiteralExpression);
                    if (valueExpression == null) {
                        return false;
                    }
                    Computable<OdooModelClass> modelClassResolver = OdooModelUtils.getRecordValueContextResolver(valueExpression);
                    if (modelClassResolver != null || maybeContainFieldReferences(valueExpression)) {
                        context.put(OdooFieldReferenceProvider.MODEL_CLASS_RESOLVER, modelClassResolver);
                        return true;
                    }
                    return false;
                }
            });

    private static boolean maybeContainFieldReferences(PsiElement element) {
        return PyPsiUtils.isMethodContext(element);
    }

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> READ_FIELDS_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("readFields") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression, ProcessingContext context) {
                    PsiElement parent = pyStringLiteralExpression.getParent();
                    if (parent instanceof PyListLiteralExpression) {
                        PsiElement arg = parent;
                        parent = parent.getParent();
                        if (parent instanceof PyKeywordArgument) {
                            parent = parent.getParent();
                        }
                        if (parent instanceof PyArgumentList) {
                            parent = parent.getParent();
                            if (parent instanceof PyCallExpression) {
                                PyCallExpression callExpression = (PyCallExpression) parent;
                                PyExpression callee = callExpression.getCallee();
                                if (callee instanceof PyReferenceExpression) {
                                    PyReferenceExpression referenceExpression = (PyReferenceExpression) callee;
                                    if ((OdooNames.READ.equals(referenceExpression.getName())
                                            && arg.equals(callExpression.getArgument(0, "fields", PyExpression.class)))
                                            || (ArrayUtil.contains(referenceExpression.getName(), OdooNames.SEARCH_READ, OdooNames.READ_GROUP)
                                            && arg.equals(callExpression.getArgument(1, "fields", PyExpression.class)))
                                            || (OdooNames.READ_GROUP.equals(referenceExpression.getName())
                                            && arg.equals(callExpression.getArgument(2, "groupby", PyExpression.class))
                                            && !pyStringLiteralExpression.getStringValue().contains(":"))) {
                                        PyExpression qualifier = referenceExpression.getQualifier();
                                        if (qualifier != null) {
                                            context.put(OdooFieldReferenceProvider.MODEL_CLASS_RESOLVER, () -> {
                                                PyType type = OdooPyUtils.getType(qualifier);
                                                if (type instanceof OdooModelClassType) {
                                                    return ((OdooModelClassType) type).getPyClass();
                                                }
                                                return null;
                                            });
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return false;
                }
            });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> ORDER_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression, ProcessingContext context) {
                    PsiElement parent = pyStringLiteralExpression.getParent();
                    if (parent instanceof PyKeywordArgument) {
                        parent = parent.getParent();
                    }
                    if (parent instanceof PyArgumentList) {
                        parent = parent.getParent();
                        if (parent instanceof PyCallExpression) {
                            PyCallExpression callExpression = (PyCallExpression) parent;
                            PyExpression callee = callExpression.getCallee();
                            if (callee instanceof PyReferenceExpression) {
                                PyReferenceExpression referenceExpression = (PyReferenceExpression) callee;
                                if ((ArrayUtil.contains(referenceExpression.getName(), OdooNames.SEARCH, OdooNames.SEARCH_READ)
                                        && pyStringLiteralExpression.equals(callExpression.getKeywordArgument("order")))
                                        || (OdooNames.READ_GROUP.equals(referenceExpression.getName())
                                        && pyStringLiteralExpression.equals(callExpression.getKeywordArgument("orderby")))
                                        || (OdooNames.SORTED.equals(referenceExpression.getName())
                                        && pyStringLiteralExpression.equals(callExpression.getArgument(0, "key", PyExpression.class)))) {
                                    PyExpression qualifier = referenceExpression.getQualifier();
                                    if (qualifier != null) {
                                        context.put(OdooFieldReferenceProvider.IS_SORT_ORDER, true);
                                        context.put(OdooFieldReferenceProvider.MODEL_CLASS_RESOLVER, () -> {
                                            PyType type = OdooPyUtils.getType(qualifier);
                                            if (type instanceof OdooModelClassType) {
                                                return ((OdooModelClassType) type).getPyClass();
                                            }
                                            return null;
                                        });
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                    return false;
                }
            });

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> OTHERS_PATTERN =
            psiElement(PyStringLiteralExpression.class).with(new PatternCondition<PyStringLiteralExpression>("") {
                @Override
                public boolean accepts(@NotNull PyStringLiteralExpression pyStringLiteralExpression,
                                       ProcessingContext context) {
                    String value = pyStringLiteralExpression.getStringValue().trim();
                    if (!(value.matches("\\w+")) || !maybeContainFieldReferences(pyStringLiteralExpression)) {
                        return false;
                    }
                    PsiElement parent = pyStringLiteralExpression.getParent();
                    if (parent instanceof PySubscriptionExpression) {
                        return true;
                    }
                    if (parent instanceof PyArgumentList) {
                        PyExpression[] args = ((PyArgumentList) parent).getArguments();
                        if (args.length > 0 && args[0].equals(pyStringLiteralExpression)) {
                            parent = parent.getParent();
                            if (parent instanceof PyCallExpression) {
                                PyExpression callee = ((PyCallExpression) parent).getCallee();
                                if (callee instanceof PyReferenceExpression) {
                                    if ("get".equals(((PyReferenceExpression) callee).getReferencedName())) {
                                        PyExpression qualifier = ((PyReferenceExpression) callee).getQualifier();
                                        if (qualifier instanceof PyReferenceExpression) {
                                            String qualifierName = ((PyReferenceExpression) qualifier).getReferencedName();
                                            if (qualifierName != null && !qualifierName.contains("context")) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (parent instanceof PyBinaryExpression) {
                        return ((PyBinaryExpression) parent).isOperator("in")
                                || ((PyBinaryExpression) parent).isOperator("notin");
                    }
                    return false;
                }
            });

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        OdooFieldReferenceProvider provider = new OdooFieldReferenceProvider();
        registrar.registerReferenceProvider(MODEL_INHERITS_PATTERN, provider);
        registrar.registerReferenceProvider(MODEL_REC_NAME_PATTERN, provider);
        registrar.registerReferenceProvider(MODEL_ORDER_PATTERN, provider);
        registrar.registerReferenceProvider(MAPPED_PATTERN, provider);
        registrar.registerReferenceProvider(DECORATOR_PATTERN, provider);
        registrar.registerReferenceProvider(ONE2MANY_INVERSE_NAME_PATTERN, provider);
        registrar.registerReferenceProvider(RELATED_PATTERN, provider);
        registrar.registerReferenceProvider(CURRENCY_FIELD_PATTERN, provider);
        registrar.registerReferenceProvider(SEARCH_DOMAIN_PATTERN, provider);
        registrar.registerReferenceProvider(RECORD_VALUE_PATTERN, provider);
        registrar.registerReferenceProvider(READ_FIELDS_PATTERN, provider);
        registrar.registerReferenceProvider(ORDER_PATTERN, provider);
        registrar.registerReferenceProvider(OTHERS_PATTERN, provider, PsiReferenceRegistrar.LOWER_PRIORITY);
    }
}
