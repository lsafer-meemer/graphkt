/*
 *	Copyright 2022 cufy.org
 *
 *	Licensed under the Apache License, Version 2.0 (the "License");
 *	you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *	    http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS,
 *	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *	See the License for the specific language governing permissions and
 *	limitations under the License.
 */
package org.cufy.graphkt.java.internal

import org.cufy.graphkt.schema.*

/* ============= ------------------ ============= */

internal fun <T> TransformContext.addType(
    type: GraphQLType<T>,
    nullable: Boolean = false
): JavaGraphQLType {
    val java = when (type) {
        is GraphQLNullableType<*> ->
            return addType(type.type, true)

        is GraphQLArrayType<*> ->
            JavaGraphQLList.list(addType(type.type))

        is GraphQLTypeReference<*> ->
            JavaGraphQLTypeReference.typeRef(type.name)

        is GraphQLOutputType<*> ->
            return addOutputType(type, nullable)

        is GraphQLInputType<*> ->
            return addInputType(type, nullable)

        else -> error("Unexpected Type $type")
    }

    if (nullable)
        return java

    return JavaGraphQLNonNull.nonNull(java)
}

/* ============= ------------------ ============= */

/**
 * - [GraphQLObjectType]
 * - [GraphQLInterfaceType]
 * - [GraphQLOutputArrayType]
 * - [GraphQLScalarType]
 * - [GraphQLEnumType]
 * - [GraphQLOutputNullableType]
 */
internal fun <T> TransformContext.addOutputType(
    type: GraphQLOutputType<T>,
    nullable: Boolean = false
): JavaGraphQLOutputType {
    val java = when (type) {
        is GraphQLOutputNullableType<*> ->
            return addOutputType(type.type, true)

        is GraphQLOutputArrayType<*> ->
            JavaGraphQLList.list(addOutputType(type.type))

        is GraphQLTypeReference<*> ->
            JavaGraphQLTypeReference.typeRef(type.name)

        is GraphQLUnionType<*> -> addUnionType(type)
        is GraphQLEnumType<*> -> addEnumType(type)
        is GraphQLScalarType<*> -> addScalarType(type)
        is GraphQLInterfaceType<*> -> addInterfaceType(type)
        is GraphQLObjectType<*> -> addObjectType(type)

        else -> error("Unexpected Output Type $type")
    }

    if (nullable)
        return java

    return JavaGraphQLNonNull.nonNull(java)
}

/* ============= ------------------ ============= */

/**
 * - [GraphQLInputArrayType]
 * - [GraphQLScalarType]
 * - [GraphQLEnumType]
 * - [GraphQLInputNullableType]
 * - [GraphQLInputObjectType]
 */
internal fun <T> TransformContext.addInputType(
    type: GraphQLInputType<T>,
    nullable: Boolean = false
): JavaGraphQLInputType {
    val java = when (type) {
        is GraphQLInputNullableType<*> ->
            return addInputType(type.type, true)

        is GraphQLInputArrayType<*> ->
            JavaGraphQLList.list(addInputType(type.type))

        is GraphQLTypeReference<*> ->
            JavaGraphQLTypeReference.typeRef(type.name)

        is GraphQLEnumType<*> -> addEnumType(type)
        is GraphQLScalarType<*> -> addScalarType(type)
        is GraphQLInputObjectType<*> -> addInputObjectType(type)

        else -> error("Unexpected Input Type Type $type")
    }

    if (nullable)
        return java

    return JavaGraphQLNonNull.nonNull(java)
}

/* ============= ------------------ ============= */
/* ============= ------------------ ============= */
/* ============= ------------------ ============= */

internal fun <T : Any> TransformContext.addScalarType(
    type: GraphQLScalarType<T>
): JavaGraphQLScalarType {
    if (type in scalarTypes)
        return scalarTypes[type] ?: error("Recursion is not supported for scalar types")

    scalarTypes[type] = null

    //

    val name = type.name
    val description = type.description
    val coercing = createJavaCoercing(type)
    val directives = type.directives.map {
        transformGraphQLDirective(it)
    }
    val specifiedByUrl = type.obtainSpecifiedByUrl()

    //

    val java = JavaGraphQLScalarType
        .newScalar()
        .name(name)
        .description(description)
        .replaceAppliedDirectives(directives)
        .specifiedByUrl(specifiedByUrl)
        .coercing(coercing)
        .build()

    scalarTypes[type] = java

    return java
}

/* ============= ------------------ ============= */

@Suppress("UNCHECKED_CAST")
internal fun <T, R> TransformContext.addEnumType(
    type: GraphQLEnumType<T>
): R where R : JavaGraphQLNamedInputType, R : JavaGraphQLNamedOutputType {
    if (type in enumTypes)
        return enumTypes[type] as? R ?: JavaGraphQLTypeReference.typeRef(type.name) as R

    enumTypes[type] = null

    //

    val name = type.name
    val description = type.description
    val values = type.values.map {
        transformGraphQLEnumValueDefinition(it)
    }
    val directives = type.directives.map {
        transformGraphQLDirective(it)
    }

    //

    val java = graphql.schema.GraphQLEnumType
        .newEnum()
        .name(name)
        .description(description)
        .replaceValues(values)
        .replaceAppliedDirectives(directives)
        .build()

    //

    enumTypes[type] = java

    return java as R
}

/* ============= ------------------ ============= */

internal fun <T : Any> TransformContext.addInputObjectType(
    type: GraphQLInputObjectType<T>
): JavaGraphQLNamedInputType {
    if (type in inputObjectTypes)
        return inputObjectTypes[type] ?: JavaGraphQLTypeReference.typeRef(type.name)

    inputObjectTypes[type] = null

    //

    val name = type.name
    val description = type.description
    val fields = type.fields.map {
        transformGraphQLInputFieldDefinition(it)
    }
    val directives = type.directives.map {
        transformGraphQLDirective(it)
    }

    //

    val java = graphql.schema.GraphQLInputObjectType.newInputObject()
        .name(name)
        .description(description)
        .replaceAppliedDirectives(directives)
        .replaceFields(fields)
        .build()

    //

    inputObjectTypes[type] = java

    return java
}

/* ============= ------------------ ============= */

internal fun <T : Any> TransformContext.addUnionType(
    type: GraphQLUnionType<T>
): JavaGraphQLNamedOutputType {
    if (type in unionTypes)
        return unionTypes[type] ?: JavaGraphQLTypeReference.typeRef(type.name)

    unionTypes[type] = null

    //

    val name = type.name
    val description = type.description
    val types = type.types.map { addObjectType(it) }
    val directives = type.directives.map {
        transformGraphQLDirective(it)
    }

    addTypeGetters(type)

    //

    val java = JavaGraphQLUnionType
        .newUnionType()
        .name(name)
        .description(description)
        .replacePossibleTypes(types)
        .replaceAppliedDirectives(directives)
        .build()

    //

    unionTypes[type] = java

    return java
}

private fun <T : Any> TransformContext.addTypeGetters(
    type: GraphQLUnionType<T>
) {
    val typeResolver = with(runtime) { createJavaTypeResolver(type.typeGetter) }

    codeRegistry.typeResolver(type.name, typeResolver)
}

/* ============= ------------------ ============= */

internal fun <T : Any> TransformContext.addInterfaceType(
    type: GraphQLInterfaceType<T>
): JavaGraphQLNamedOutputType {
    if (type in interfaceTypes)
        return interfaceTypes[type] ?: JavaGraphQLTypeReference.typeRef(type.name)

    interfaceTypes[type] = null

    //

    val allInterfaces = type.generateAllInterfacesSequence().toList()

    val allFields = (allInterfaces.flatMap { it.fields } + type.fields)

    val typeResolver = with(runtime) { createJavaTypeResolver(type.typeGetter) }

    codeRegistry.typeResolver(type.name, typeResolver)

    //

    val name = type.name
    val description = type.description
    val interfaces = type.interfaces.map {
        addInterfaceType(it)
    }
    val fields = allFields.map {
        transformGraphQLFieldDefinition(it)
    }
    val directives = type.directives.map {
        transformGraphQLDirective(it)
    }

    //

    val java = graphql.schema.GraphQLInterfaceType
        .newInterface()
        .name(name)
        .description(description)
        .replaceFields(fields)
        .replaceInterfacesOrReferences(interfaces)
        .replaceAppliedDirectives(directives)
        .build()

    //

    interfaceTypes[type] = java

    return java
}

/* ============= ------------------ ============= */

internal fun <T : Any> TransformContext.addObjectType(
    type: GraphQLObjectType<T>
): JavaGraphQLNamedOutputType {
    if (type in objectTypes)
        return objectTypes[type] ?: JavaGraphQLTypeReference.typeRef(type.name)

    objectTypes[type] = null

    //

    val allInterfaces = type.generateAllInterfacesSequence().toList()

    @Suppress("UNCHECKED_CAST")
    val allGetterBlocks = (allInterfaces.flatMap { it.onGetBlocks } + type.onGetBlocks)
            as List<GraphQLGetterBlock<T, *>>

    @Suppress("UNCHECKED_CAST")
    val allGetterBlockingBlocks = (allInterfaces.flatMap { it.onGetBlockingBlocks } + type.onGetBlockingBlocks)
            as List<GraphQLGetterBlockingBlock<T, *>>

    val allFields = (allInterfaces.flatMap { it.fields } + type.fields)

    allFields.forEach {
        @Suppress("UNCHECKED_CAST")
        it as GraphQLFieldDefinition<T, Any?>

        val coordinates = JavaFieldCoordinates.coordinates(type.name, it.name)
        val fetcher = with(runtime) {
            createJavaDataFetcher(
                getter = it.getter,
                onGetBlocks = allGetterBlocks + it.onGetBlocks,
                onGetBlockingBlocks = allGetterBlockingBlocks + it.onGetBlockingBlocks,
                definition = it
            )
        }

        codeRegistry.dataFetcher(coordinates, fetcher)
    }

    //

    val name = type.name
    val description = type.description
    val interfaces = type.interfaces.map {
        addInterfaceType(it)
    }
    val fields = allFields.map {
        transformGraphQLFieldDefinition(it)
    }
    val directives = type.directives.map {
        transformGraphQLDirective(it)
    }

    //

    val java = graphql.schema.GraphQLObjectType
        .newObject()
        .name(name)
        .description(description)
        .replaceFields(fields)
        .replaceInterfaces(interfaces)
        .replaceAppliedDirectives(directives)
        .build()

    //

    objectTypes[type] = java

    return java
}

/* ============= ------------------ ============= */

internal fun TransformContext.addDirectiveDefinition(
    definition: GraphQLDirectiveDefinition
): JavaGraphQLDirective {
    if (definition in directives)
        return directives[definition] ?: error("Recursion is not supported for directive definitions")

    directives[definition] = null

    //

    val name = definition.name
    val description = definition.description
    val repeatable = definition.repeatable
    val locations = definition.locations.map {
        transformGraphQLDirectiveLocation(it)
    }
    val arguments = definition.arguments.map {
        transformGraphQLArgumentDefinition(it)
    }

    //

    val java = JavaGraphQLDirective.newDirective()
        .name(name)
        .description(description)
        .repeatable(repeatable)
        .apply { locations.forEach { validLocation(it) } }
        .replaceArguments(arguments)
        .build()

    //

    directives[definition] = java

    return java
}
