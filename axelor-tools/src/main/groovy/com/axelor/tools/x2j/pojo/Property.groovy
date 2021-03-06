/**
 * Copyright (c) 2012-2013 Axelor. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public
 * Attribution License Version 1.0 (the “License”); you may not use
 * this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://license.axelor.com/.
 *
 * The License is based on the Mozilla Public License Version 1.1 but
 * Sections 14 and 15 have been added to cover use of software over a
 * computer network and provide for limited attribution for the
 * Original Developer. In addition, Exhibit A has been modified to be
 * consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an “AS IS”
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is part of "Axelor Business Suite", developed by
 * Axelor exclusively.
 *
 * The Original Developer is the Initial Developer. The Initial Developer of
 * the Original Code is Axelor.
 *
 * All portions of the code written by Axelor are
 * Copyright (c) 2012-2013 Axelor. All Rights Reserved.
 */
package com.axelor.tools.x2j.pojo

import groovy.util.slurpersupport.NodeChild

import com.axelor.tools.x2j.Utils
import com.google.common.base.CaseFormat
import com.sun.jersey.core.impl.provider.entity.Inflector

class Property {

	String name

	String type

	String code

	String target

	Entity entity

	private Map<String, Object> attrs = new HashMap()

	private Property(Entity entity, String name, String type) {
		this.entity = entity
		this.name = name
		this.type = type
		this.code = ""
		this.target = ""
	}

	Property(Entity entity, NodeChild node) {
		this.entity = entity
		name = node.@name
		type = node.name()
		code = node.text()
		target = entity.importType(node.@ref.toString())
		attrs = node.attributes()

		if (!name) {
			throw new IllegalArgumentException("Property name not given.")
		}
	}

	String getType() {
		switch(type) {
			case "string":
				return "String"
			case "integer":
				return "Integer"
			case "long":
				return "Long"
			case "boolean":
				return "Boolean"
			case ["double", "float"]:
				return "Double"
			case "decimal":
				return entity.importType("java.math.BigDecimal")
			case "date":
				return entity.importType("org.joda.time.LocalDate")
			case "time":
				return entity.importType("org.joda.time.LocalTime")
			case "datetime":
				def t = attrs['tz'] == 'true' ? 'DateTime' : 'LocalDateTime'
				return entity.importType('org.joda.time.' + t)
			case "binary":
				return "byte[]"
			case "one-to-one":
			case "many-to-one":
				return entity.importType(target)
			case "one-to-many":
				return entity.importType("java.util.List<$target>")
			case "many-to-many":
				return entity.importType("java.util.Set<$target>")
		}
		throw new IllegalArgumentException("Invalid type: " + type)
	}

	String getServerType() {
		return type
	}

	boolean isSimple() {
		switch(type) {
			case "string":
				if (attrs["large"] == "true" || attrs["multiline"] == "true") return false
				return true
			case "integer":
			case "long":
			case "boolean":
			case ["double", "float"]:
			case "decimal":
			case "date":
			case "time":
			case "datetime":
				return true
		}
		return false
	}

	String getValue() {

		String value = attrs['default']

		if (value == null || "" == value.trim()) {
			return this.getEmptyValue()
		}

		switch(type) {
			case "boolean":
				return value ==~ /(?i)true|t|1|Boolean\\.TRUE/ ? "Boolean.TRUE" : "Boolean.FALSE"
			case "string":
				return "\"${value}\""
			case "long":
				return value.endsWith("L") ? value : "${value}L"
			case [ "integer", "double", "boolean" ]:
				return value
			case "decimal":
				return "new BigDecimal(\"${value}\")"
			case "date":
				return value == "now" ? "new LocalDate()" : "new LocalDate(\"${value}\")"
			case "time":
				return value == "now" ? "new LocalTime()" : "new LocalTime(\"${value}\")"
			case "datetime":
				def t = attrs['tz'] == 'true' ? 'DateTime' : 'LocalDateTime'
				return value == "now" ? "new ${t}()" : "new ${t}(\"${value}\")"
		}
	}

	String getDefaultExpression() {
		if (name == "id" || name == "version")
			return ""
		return this.getValue() == null ? "" : " = ${value}"
	}

	String getEmptyValue() {

		if (isNullable()) {
			return null
		}

		switch(type) {
			case "boolean":
				return "Boolean.FALSE"
			case "integer":
				return "0"
			case "long" :
				return "0L"
			case "decimal":
				return "BigDecimal.ZERO"
		}
		return null
	}

	String getGetter() {
		"get" + firstUpper(name)
	}

	String getSetter() {
		"set" + firstUpper(name)
	}

	String getGetterBody() {

		if (name == "id" || name == "version") {
			return "return $name;"
		}

		def result = []
		def empty = this.getEmptyValue()

		if (empty != null) {
			result += "if ($name == null) return $empty;"
		}
		result += "return $name;"
		result = result.collect { "        " + it }
		return result.join("\n").trim()
	}

	String getSetterBody() {
		return "this.$name = $name;"
	}

	String getLinkCode() {
		def mapped = attrs["mappedBy"]
		if (!mapped || type != "one-to-many") {
			return null
		}
		return "item.set" + firstUpper(mapped) + "(this);"
	}

	String getDelinkCode() {
		def mapped = attrs["mappedBy"]
		def orphan = attrs["orphan"] == "true"
		if (!orphan || !mapped || type != "one-to-many") {
			return null
		}
		return "item.set" + firstUpper(mapped) + "(null);"
	}

	String getDelinkAllCode() {
		def mapped = attrs["mappedBy"]
		def orphan = attrs["orphan"] == "true"
		if (!orphan || !mapped || type != "one-to-many") {
			return null
		}
		return """for(${target} item : ${name}) {
				item.set${firstUpper(mapped)}(null);
			}"""
	}

	String getSingularName() {
		return getSingularName(name)
	}

	String getSingularName(String name) {
		if (name =~ /(Set|List)$/) {
			return name + "Item"
		}
		return Inflector.getInstance().singularize(name)
	}

	String getMappedBy() {
		return attrs["mappedBy"]
	}

	String getColumn() {
		String col = attrs['column']
		if (!col || col.trim().empty) {
			return null
		}
		return col
	}

	String getColumnAuto() {
		String col = getColumn()
		if (col) {
			return col
		}
		return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name)
	}

	boolean isInitParam() {
		return attrs["initParam"] == "true"
	}

	boolean isNullable() {
		return attrs["nullable"] == "true" && attrs["required"] != "true"
	}

	boolean isOrphan() {
		return attrs["orphan"] == "true"
	}

	boolean isUnique() {
		return attrs["unique"] == "true"
	}

	boolean isHashKey() {
		if (name == "id" || name == "version") return false
		if (attrs["hashKey"] == "false") return false
		if (attrs["hashKey"] == "true" || isUnique()) return true
		return entity.hashAll && isSimple() && !isVirtual()
	}

	Object getAttribute(String name) {
		return attrs[name]
	}

	String newCollection() {
		if (type == "many-to-many") {
			importName("java.util.HashSet")
			return "new HashSet<$target>()"
		}
		importName("java.util.ArrayList")
		return "new ArrayList<$target>()"
	}

	String firstUpper(String string) {
		string.substring(0, 1).toUpperCase() + string.substring(1)
	}

	String getCode() {
		return Utils.stripCode(this.code, "\n\t\t")
	}

	String getFormula() {
		String text = this.code
		if (text == null) {
			return ""
		}
		text = text.replaceAll("\"", '''\\\\"''')
		text = Utils.stripCode(text, "\n\t\t\"")

		text = "\"(" + text.replaceAll("\n", "\" +\n") + ")\""

		if (text.indexOf('\n') != text.lastIndexOf('\n')) {
			text = "\n\t\t" + text
		}

		return text
	}

	String getDocumentation() {
		String text = Utils.stripCode(attrs.get("help"), "\n * ")
		if (text == "") {
			return ""
		}
		return """
\t/**
\t * """ + text + """
\t */"""
	}

	String importName(String qname) {
		entity.importType(qname)
	}

	boolean isReference() {
		type == "many-to-one" || type == "one-to-one"
	}

	boolean isCollection() {
		type == "one-to-many" || type == "many-to-many"
	}

	boolean isTransient() {
		return attrs["transient"] == "true"
	}

	boolean isNameField() {
		return attrs.namecolumn == "true"
	}

	boolean isVirtual() {
		return code != null && code.trim().length() > 0
	}

	boolean isFormula() {
		return attrs.formula == 'true' && !isCollection()
	}

	boolean isIndexable() {
		if (this.isFormula() || this.isTransient() || attrs['index'] == 'false')
			return false
		String index = attrs['index'] as String
		return index =~ /true|^idx_/ ||
			attrs['namecolumn'] == 'true' ||
			name in ['name', 'code'] ||
			this.isReference() && !attrs['mappedBy']
	}

	static Property idProperty(Entity entity) {
		new Property(entity, "id", "long")
	}

	List<Annotation> getAnnotations() {
		[
			$id(),
			$hashKey(),
			$widget(),
			$binary(),
			$nameColumn(),
			$virtual(),
			$required(),
			$size(),
			$digits(),
			$index(),
			$transient(),
			$column(),
			$one2one(),
			$many2one(),
			$one2many(),
			$many2many(),
			$orderBy()
		]
		.grep { it != null }
		.flatten()
		.grep { Annotation a ->
			!a.empty
		}
	}

	private Annotation annon(String name) {
		return annon(name, false)
	}

	private Annotation annon(String name, boolean empty) {
		return new Annotation(entity, name, empty)
	}

	private Annotation $column() {

		def column = attrs.column
		def unique = attrs.unique

		if (column == null && unique == null)
			return null

		annon(reference ? "javax.persistence.JoinColumn" : "javax.persistence.Column")
				.add("name", column)
				.add("unique", unique, false)
	}

	private Annotation $transient() {
		if (isTransient()) {
			return annon("javax.persistence.Transient", true)
		}
	}

	private List<Annotation> $size() {

		def min = attrs.min
		def max = attrs.max

		if (min == null && max == null)
			return null

		def all = []

		switch (type) {
			case "decimal":
				if (min != null) all += annon("javax.validation.constraints.DecimalMin").add(min)
				if (max != null) all += annon("javax.validation.constraints.DecimalMax").add(max)
				return all
			case "string":
				return [
					annon("javax.validation.constraints.Size")
					.add("min", min, false)
					.add("max", max, false)
				]
		}

		if (min != null) all += annon("javax.validation.constraints.Min").add(min, false)
		if (max != null) all += annon("javax.validation.constraints.Max").add(max, false)

		return all
	}

	private Annotation $digits() {

		def precision = attrs['precision']
		def scale = attrs['scale']

		if (precision == null && scale == null)
			return null

		precision = precision as Integer
		scale = scale as Integer

		annon("javax.validation.constraints.Digits", false)
				.add("integer", (precision - scale) as String, false)
				.add("fraction", scale as String, false)
	}

	private Annotation $required() {
		if (attrs.required == "true")
			annon("javax.validation.constraints.NotNull", true)
	}

	private List<Annotation> $virtual() {
		if (!this.isVirtual()) {
			return null
		}
		def all = [annon("com.axelor.db.annotations.VirtualColumn", true)]
		if (this.isTransient()) {
			return all
		}

		if (this.isFormula()) {
			all += [annon(reference ? "org.hibernate.annotations.JoinFormula" : "org.hibernate.annotations.Formula")
							.add(this.getFormula(), false)]
		} else {
			all += [annon("javax.persistence.Access").add(
						entity.importType("javax.persistence.AccessType.PROPERTY"), false)]
		}
		return all
	}

	private Annotation $nameColumn() {
		if (isNameField())
			annon("com.axelor.db.annotations.NameColumn", true)
	}

	private Annotation $widget() {

		def title = attrs['title']
		def help = attrs['help']
		def readonly = attrs['readonly']
		def hidden = attrs['hidden']
		def search = attrs['search']
		def multiline = attrs['multiline']
		def selection = attrs['selection']
		def image = attrs['image']

		if (selection) {
			selection = selection.replaceAll("\\],\\s*\\[", '], [')
		}

		if (title || help || readonly || hidden || multiline || selection || image)
			annon("com.axelor.db.annotations.Widget")
				.add("image", image, false)
				.add("title", title)
				.add("help", help)
				.add("readonly", readonly, false)
				.add("hidden", hidden, false)
				.add("multiline", multiline, false)
				.add("search", search, true, true)
				.add("selection", selection)
	}

	private List<Annotation> $binary() {

		def large = attrs['large'] != null

		if (large && type == 'string') {
			return [
				annon("javax.persistence.Lob", true),
				annon("javax.persistence.Basic").add("fetch", "javax.persistence.FetchType.LAZY", false),
				annon("org.hibernate.annotations.Type").add("type", "org.hibernate.type.TextType")
			]
		}

		if (large || type == 'binary') {
			return [
				annon("javax.persistence.Lob", true),
				annon("javax.persistence.Basic").add("fetch", "javax.persistence.FetchType.LAZY", false)
			]
		}
	}

	private Annotation $index() {
		if (!this.isIndexable()) return null
		String index = attrs['index'] as String
		if (index && !index.startsWith('idx_'))
			index = "${entity.table}_${columnAuto}_IDX".toUpperCase()
		if (!index) return null
		return annon("org.hibernate.annotations.Index").add("name", index)
	}

	private List<Annotation> $id() {

		if (name != "id")
			return null

		if (!entity.sequential) {
			return [
				annon("javax.persistence.Id", true),
				annon("javax.persistence.GeneratedValue")
					.add("strategy", "javax.persistence.GenerationType.AUTO", false)
			]
		}

		def name = entity.table + '_SEQ'

		[
			annon("javax.persistence.Id", true),
			annon("javax.persistence.GeneratedValue")
				.add("strategy", "javax.persistence.GenerationType.SEQUENCE", false)
				.add("generator", name),
			annon("javax.persistence.SequenceGenerator")
				.add("name", name)
				.add("sequenceName", name)
				.add("allocationSize", "1", false)
		]
	}

	private Annotation $one2one() {
		if (type != "one-to-one") return null

		def mapped = attrs.get('mappedBy')
		def orphan = attrs.containsKey('orphan') ? attrs.get('orphan') : true

		def a = annon("javax.persistence.OneToOne")
			.add("fetch", "javax.persistence.FetchType.LAZY", false)
			.add("mappedBy", mapped)

		if (orphan) {
			a.add("cascade", ["javax.persistence.CascadeType.PERSIST","javax.persistence.CascadeType.MERGE"], false)
		} else {
			a.add("cascade", "javax.persistence.CascadeType.ALL", false)
			a.add("orphanRemoval", "true", false)
		}
		return a
	}

	private Annotation $many2one() {
		if (type != "many-to-one") return null

		annon("javax.persistence.ManyToOne")
			.add("fetch", "javax.persistence.FetchType.LAZY", false)
			.add("cascade", ["javax.persistence.CascadeType.PERSIST", "javax.persistence.CascadeType.MERGE"], false)
	}

	private Annotation $one2many() {

		if (type != "one-to-many") return null

		def mapped = attrs.get('mappedBy')
		def orphan = attrs.get('orphan')

		def a = annon("javax.persistence.OneToMany")
			.add("fetch", "javax.persistence.FetchType.LAZY", false)
			.add("mappedBy", mapped)

		if (orphan != null) {
			a.add("cascade", ["javax.persistence.CascadeType.PERSIST", "javax.persistence.CascadeType.MERGE"], false)
		} else {
			a.add("cascade", "javax.persistence.CascadeType.ALL", false)
			a.add("orphanRemoval", "true", false)
		}
		return a
	}

	private Annotation $many2many() {
		def mapped = attrs.get('mappedBy')
		if (type == "many-to-many")
			annon("javax.persistence.ManyToMany")
				.add("fetch", "javax.persistence.FetchType.LAZY", false)
				.add("mappedBy", mapped)
				.add("cascade", ["javax.persistence.CascadeType.PERSIST", "javax.persistence.CascadeType.MERGE"], false)
	}

	private Annotation $orderBy() {
		def orderBy = attrs.get('orderBy')?.trim()
		if (!orderBy) return null

		orderBy = orderBy.split(/,/).collect {
			it.trim().replaceAll(/-\s*(\w+)/, '$1 DESC')
		}.join(", ")

		return annon("javax.persistence.OrderBy").add(orderBy)
	}

	private Annotation $hashKey() {
		if (!hashKey) return null
		return annon("com.axelor.db.annotations.HashKey", true)
	}
}
