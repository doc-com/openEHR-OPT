package com.cabolabs.openehr.opt.parser

import com.cabolabs.openehr.opt.model.*
import com.cabolabs.openehr.opt.model.primitive.*
import com.cabolabs.openehr.opt.model.domain.*
import com.cabolabs.openehr.opt.model.datatypes.*
//import com.thoughtworks.xstream.XStream
import groovy.util.slurpersupport.GPathResult

@groovy.util.logging.Log4j
class OperationalTemplateParser {

   // Parsed XML
   //GPathResult templateXML

   // Instance to be generated by parsing the XML
   OperationalTemplate template

   /**
    * Parses the XML contents of a template. This class doesn't know where
    * the template is located, e.g. filesystem, web service, etc.
    */
   OperationalTemplate parse(String templateContents)
   {
      def templateXML = new XmlSlurper().parseText( templateContents ) // GPathResult

      // TODO: validate against XSD

      return parseOperationalTemplate(templateXML)
   }

   private parseOperationalTemplate(GPathResult tpXML)
   {
      this.template = new OperationalTemplate(
         uid: tpXML.uid.value.text(),
         templateId: tpXML.template_id.value.text(),
         concept: tpXML.concept.text(),
         language: _parseCodePhrase(tpXML.language),
         isControlled: ((!tpXML.is_controlled.isEmpty() && tpXML.is_controlled.text() != '') ? tpXML.is_controlled.text() : false),
         purpose: tpXML.description.details.purpose.text(),
         // TODO: add use, misuse, keywords, etc. from RESOURCE_DESCRIPTION_ITEM: https://github.com/openEHR/java-libs/blob/f6ee434226bf926d261c2690016c1d6022b877be/oet-parser/src/main/xsd/Resource.xsd
      )

      this.template.definition = parseObjectNode(tpXML.definition, '/', '/', '/', '/')


      // Second pass to set the text and description for all the ObjectNodes
      // That needs the whole structure parsed, that is why this couldn't be done
      // inside the parsing process itself.
      setTextRecursive(this.template.definition, "")

      /*
      // to set the obn text and description, the parent should be set, that is why this is defined here.
      def parent_root = obn.findParentRoot()
      if (!obn.archetypeId)
      {
         println "obn "+ obn.nodeId +" "+ obn.type +" "+ obn.rmTypeName +" no tiene archetypeId"
         println "parent root arch id is "+ parent_root.archetypeId
      }
      obn.text = this.template.getTerm(parent_root.archetypeId, obn.nodeId)
      obn.description = this.template.getDescription(parent_root.archetypeId, obn.nodeId)
      */

      // DEBUG
      /*
      def xstream = new XStream()
      def xml = xstream.toXML(this.template)
      def random = new Random()
      def randomInt = random.nextInt(20000)
      new File("template_"+ randomInt +".xml").write( xml )
      */
      // /DEBUG

      return this.template
   }

   private setTextRecursive(ObjectNode obn, String rootArchetypeId)
   {
      if (obn.type == 'C_ARCHETYPE_ROOT') rootArchetypeId = obn.archetypeId

      // text is null for nodes with no nodeId, for those, the text would be
      // the parents + the correspondent attribute, e.g.
      // nodeID > attr > NO_nodeId
      // text   > .... > text+attr
      if (obn.nodeId)
      {
         obn.text = this.template.getTerm(rootArchetypeId, obn.nodeId)
         obn.description = this.template.getDescription(rootArchetypeId, obn.nodeId)
      }
      else
      {
         obn.text = obn.parent.parent.text +'.'+ obn.parent.rmAttributeName
         obn.description = obn.parent.parent.description +'.'+ obn.parent.rmAttributeName
      }

      obn.attributes.each { attr ->
         setTextRecursive(attr, rootArchetypeId)
      }
   }
   private setTextRecursive(AttributeNode atn, String rootArchetypeId)
   {
      atn.children.each { obn->
         setTextRecursive(obn, rootArchetypeId)
      }
   }

   private _parseCodePhrase(GPathResult node)
   {
      return node.terminology_id.value.text() +"::"+ node.code_string.text()
   }

   private parseCodePhrase(GPathResult node)
   {
      return new CodePhrase(codeString: node.code_string.text(), terminologyId: node.terminology_id.value.text())
   }

   private parseIntervalInt(GPathResult node)
   {
      if (node.isEmpty()) return null

      def itv = new IntervalInt(
         upperIncluded:  node.upper_included.text().toBoolean(),
         lowerIncluded:  node.lower_included.text().toBoolean(),
         upperUnbounded: node.upper_unbounded.text().toBoolean(),
         lowerUnbounded: node.lower_unbounded.text().toBoolean()
      )

      if (!itv.lowerUnbounded)
      {
         itv.lower = Integer.parseInt(node.lower.text())
      }
      if (!itv.upperUnbounded)
      {
         itv.upper = Integer.parseInt(node.upper.text())
      }

      return itv
   }

   private parseIntervalFloat(GPathResult node)
   {
      if (node.isEmpty()) return null

      def itv = new IntervalFloat(
         upperIncluded:  node.upper_included.text().toBoolean(),
         lowerIncluded:  node.lower_included.text().toBoolean(),
         upperUnbounded: node.upper_unbounded.text().toBoolean(),
         lowerUnbounded: node.lower_unbounded.text().toBoolean()
      )

      if (!itv.lowerUnbounded)
      {
         itv.lower = Float.parseFloat(node.lower.text())
      }
      if (!itv.upperUnbounded)
      {
         itv.upper = Float.parseFloat(node.upper.text())
      }

      return itv
   }

   private parseIntervalDuration(GPathResult node)
   {
      if (node.isEmpty()) return null

      def itv = new IntervalDuration(
         upperIncluded:  node.upper_included.text().toBoolean(),
         lowerIncluded:  node.lower_included.text().toBoolean(),
         upperUnbounded: node.upper_unbounded.text().toBoolean(),
         lowerUnbounded: node.lower_unbounded.text().toBoolean()
      )

      if (!itv.lowerUnbounded)
      {
         itv.lower = new Duration(value: node.lower.text())
      }
      if (!itv.upperUnbounded)
      {
         itv.upper = new Duration(value: node.upper.text())
      }

      return itv
   }


   /* classes that are not LOCATABLE, so don't have node_id in the path */
   def pathables = [
     'EVENT_CONTEXT',
     'ISM_TRANSITION',
     'INSTRUCTION_DETAILS'
   ]

   /**
    * path the path relative to the archetype root, existing on the OPT and archetype
    * dataPath the path existing on data instances, for PATHABLE don't include the node_id in the path
    * for instance, when path is /ism_transition[atNNNN]/current_state, dataPath is /ism_transition/current_state
    */
   private parseObjectNode(GPathResult node, String parentPath, String path, String dataPath, String templateDataPath)
   {
      // Path calculation
      def templatePath = parentPath

      if (templatePath != '/')
      {
         // comienza de nuevo con las paths relativas al root de este arquetipo
         if (!node.archetype_id.value.isEmpty())
         {
            templatePath += '[archetype_id='+ node.archetype_id.value +']' // slot in the path instead of node_id
            templateDataPath += '[archetype_id='+ node.archetype_id.value +']'

            if (node.'@xsi:type'.text() == "C_ARCHETYPE_ROOT")
            {
               path = '/' // archetype root found
               dataPath = '/' // reset data path when path is root
            }
         }
         // para tag vacia empty da false pero text es vacio ej. <node_id/>
         else if (!node.node_id.isEmpty() && node.node_id.text() != '')
         {
            templatePath += '['+ node.node_id.text() + ']'
            path += '['+ node.node_id.text() + ']'

            // avoids adding the node_id for PATHABLE nodes
            // if node is a LOCATABLE add the node_id to the dataPath
            if (!pathables.contains(node.rm_type_name.text()))
            {
               templateDataPath += '['+ node.node_id.text() + ']'
               dataPath += '['+ node.node_id.text() + ']'
            }
         }
      }


      //println "path: "+ path
      //println node.'@xsi:type'.text() +" "+ path

      // TODO: refactor individual factories per AOM type

      def obn
      if (['C_CODE_PHRASE', 'C_CODE_REFERENCE', 'CONSTRAINT_REF'].contains(node.'@xsi:type'.text()))
      {
         assert node.rm_type_name.text() == 'CODE_PHRASE'

         def terminologyRef

         // referenceSetUri is present on C_CODE_REFERENCE amd in some C_CODE_PHRASE,
         // that is a bug from modeling tools.
         def uri = node.referenceSetUri.text()
         if (uri) terminologyRef = uri


         obn = new CCodePhrase(
            owner: this.template,
            rmTypeName: node.rm_type_name.text(),
            nodeId: node.node_id.text(),
            type: node.'@xsi:type'.text(),
            archetypeId: node.archetype_id.value.text(), // This is optional, just resolved slots have archId
            templatePath: templatePath,
            path: path,
            dataPath: dataPath,
            templateDataPath: templateDataPath,
            terminologyRef: terminologyRef
         )

         if (obn.type == 'CONSTRAINT_REF')
         {
            obn.reference = node.reference.text()
         }

         // list is not present on C_CODE_REFERENCE
         if (!node.code_list.isEmpty())
         {
            node.code_list.each {
               obn.codeList << it.text()
            }

            // parse terminologyID value, we could create CODE_PHRASE and parse this internally
            // name [ ‘(’ version ‘)’ ]
            def tid = node.terminology_id.value.text()
            def tidPattern = ~/(\w+)\s*(?:\(?(\w*)\)?.*)?/
            def result = tidPattern.matcher(tid)

            obn.terminologyIdName = result[0][1]
            obn.terminologyIdVersion = result[0][2] // can be empty
         }
      }
      else if (node.'@xsi:type'.text() == 'C_DV_QUANTITY')
      {
         obn = new CDvQuantity(
            owner:        this.template,
            rmTypeName:   node.rm_type_name.text(),
            nodeId:       node.node_id.text(),
            type:         node.'@xsi:type'.text(),
            archetypeId:  node.archetype_id.value.text(), // This is optional, just resolved slots have archId
            templatePath: templatePath,
            path:         path,
            dataPath: dataPath,
            templateDataPath: templateDataPath
         )

         obn.property = parseCodePhrase(node.property)

         // CQuantityItem
         def cqi
         node.list.each {
            cqi           = new CQuantityItem()
            cqi.units     = it.units.text()
            cqi.magnitude = parseIntervalFloat(it.magnitude)
            cqi.precision = parseIntervalInt(it.precision)
            obn.list << cqi
         }
      }
      else if (node.'@xsi:type'.text() == 'C_DV_ORDINAL')
      {
         obn = new CDvOrdinal(
            owner:        this.template,
            rmTypeName:   node.rm_type_name.text(),
            nodeId:       node.node_id.text(),
            type:         node.'@xsi:type'.text(),
            archetypeId:  node.archetype_id.value.text(), // This is optional, just resolved slots have archId
            templatePath: templatePath,
            path:         path,
            dataPath: dataPath,
            templateDataPath: templateDataPath
         )

         def coi
         node.list.each {
            coi        = new CDvOrdinalItem()
            coi.value  = Integer.parseInt(it.value.text())
            coi.symbol = parseCodePhrase(it.symbol.defining_code)
            obn.list << coi
         }
      }
      else if (node.'@xsi:type'.text() == 'ARCHETYPE_SLOT')
      {
         obn = new ArchetypeSlot(
            owner:        this.template,
            rmTypeName:   node.rm_type_name.text(),
            nodeId:       node.node_id.text(),
            type:         node.'@xsi:type'.text(),
            archetypeId:  node.archetype_id.value.text(),
            templatePath: templatePath,
            path:         path,
            dataPath: dataPath,
            templateDataPath: templateDataPath
         )

         obn.includes = node.includes.expression.right_operand.item.pattern.text()
         obn.excludes = node.excludes.expression.right_operand.item.pattern.text()
      }
      else if (node.'@xsi:type'.text() == 'C_PRIMITIVE_OBJECT')
      {
         obn = new PrimitiveObjectNode(
            owner:        this.template,
            rmTypeName:   node.rm_type_name.text(),
            nodeId:       node.node_id.text(),
            type:         node.'@xsi:type'.text(),
            archetypeId:  node.archetype_id.value.text(), // This is optional, just resolved slots have archId
            templatePath: templatePath,
            path:         path,
            dataPath: dataPath,
            templateDataPath: templateDataPath
         )

         def primitive = node.item

         // FIXME: switch (primitive.'@xsi:type'.text())
         if (primitive.'@xsi:type'.text() == 'C_INTEGER')
         {
            obn.item = new CInteger()

            if (!primitive.range.isEmpty())
               obn.item.range = parseIntervalInt(primitive.range)
            else
            {
               primitive.list.each {
                  obn.item.list << Integer.parseInt(it.text())
               }
            }
         }
         else if (primitive.'@xsi:type'.text() == 'C_DATE_TIME')
         {
            obn.item = new CDateTime()
            obn.item.pattern = primitive.pattern.text()
         }
         else if (primitive.'@xsi:type'.text() == 'C_DATE')
         {
            obn.item = new CDate()
            obn.item.pattern = primitive.pattern.text()
         }
         else if (primitive.'@xsi:type'.text() == 'C_BOOLEAN')
         {
            obn.item = new CBoolean(
               trueValid: primitive.true_valid.text().toBoolean(),
               falseValid: primitive.false_valid.text().toBoolean()
            )
            /*
            <item xsi:type="C_BOOLEAN">
             <true_valid>true</true_valid>
             <false_valid>true</false_valid>
            </item>
            */
         }
         else if (primitive.'@xsi:type'.text() == 'C_DURATION')
         {
            obn.item = new CDuration()

            if (!primitive.range.isEmpty())
               obn.item.range = parseIntervalDuration(primitive.range)
            else
               obn.item.pattern = primitive.pattern.text()
         }
         else if (primitive.'@xsi:type'.text() == 'C_REAL')
         {
            obn.item = new CReal()
            obn.item.range = parseIntervalFloat(primitive.range)
         }
         else if (primitive.'@xsi:type'.text() == 'C_STRING')
         {
            obn.item = new CString()

            if (!primitive.pattern.isEmpty())
               obn.item.pattern = primitive.pattern.text()
            else
            {
               primitive.list.each {
                  obn.item.list << it.text()
               }
            }
         }
         else
         {
            throw new Exception("primitive "+primitive.'@xsi:type'.text() +" not supported")
         }
      }
      else
      {
         if ('CODE_PHRASE' == node.rm_type_name.text())
         {
            println ">>> PARSING "+ node.rm_type_name.text() +" as ObjectNode"
            println templatePath
            println node.'@xsi:type'.text()
         }
         // C_COMPLEX_OBJECTs and C_ARCHETYPE_ROOTs will be parsed here.

         // println "ObjectNode "+ node.'@xsi:type'.text()
         obn = new ObjectNode(
            owner: this.template,
            rmTypeName: node.rm_type_name.text(),
            nodeId: node.node_id.text(),
            type: node.'@xsi:type'.text(),
            archetypeId: node.archetype_id.value.text(), // This is optional, just resolved slots have archId
            templatePath: templatePath,
            path: path,
            dataPath: dataPath,
            templateDataPath: templateDataPath
            // TODO: default_values
         )
      }

      // TODO: parse occurrences

      // TODO: for C_COMPLEX_OBJECT that don't have attributes, we need to check if the rm_type_name
      //       is a DATA_VALUE, and create the correspondent constraints for "any_allowed", since
      //       if there are no constraints anything is allowed inside the DATA_VALUE (if at least
      //       complies with the DATA_VALUE definition). For instance we need to add PrimitiveObjectNode
      //       and CDateTime when we find a any_allowed for a DV_DATE_TIME. This is to validate the
      //       value at least is a valid DV_DATE_TIME, e.g. a DATE is not valid becaues it lacks the TIME part.
      // see https://github.com/ppazos/openEHR-OPT/issues/34

      node.attributes.each { xatn ->

         obn.attributes << parseAttributeNode(xatn, templatePath, path, obn, dataPath, templateDataPath)
      }

      node.term_definitions.each { tdef ->

         obn.termDefinitions << parseCodedTerm(tdef)
      }

      // Traverse all subnodes to get the flat structure for path->node
      this.setFlatNodes(obn)

      // Used by guigen and binder
      this.template.nodes[templatePath] = obn

      return obn
   }

   private setFlatNodes(ObjectNode parent)
   {
      this.template.nodes.each { path, constraint ->
         if (constraint.path.startsWith(parent.path))
            parent.nodes[constraint.path] = constraint // uses archetype paths not template paths!
      }
   }

   private parseAttributeNode(GPathResult attr, String parentPath, String path, ObjectNode parent, String dataPath, String templateDataPath)
   {
      // Path calculation
      def templatePath = parentPath
      if (templatePath == '/')
      {
         templatePath += attr.rm_attribute_name.text() // Avoids to repeat '/'
         templateDataPath += attr.rm_attribute_name.text()
      }
      else
      {
         templatePath += '/'+ attr.rm_attribute_name.text()
         templateDataPath += '/'+ attr.rm_attribute_name.text()
      }

      def nextArchPath
      if (path == '/')
      {
         nextArchPath = '/' + attr.rm_attribute_name.text()
         dataPath = '/' + attr.rm_attribute_name.text()
      }
      else
      {
         nextArchPath = path +'/'+ attr.rm_attribute_name.text()
         dataPath = dataPath +'/'+ attr.rm_attribute_name.text()
      }

      def atn = new AttributeNode(
         rmAttributeName: attr.rm_attribute_name.text(),
         type:            attr.'@xsi:type'.text(),
         parent:          parent,
         path: nextArchPath,
         dataPath: dataPath,
         templatePath: templatePath,
         templateDataPath: templateDataPath
         // TODO: cardinality
         // TODO: existence
      )

      def obn
      attr.children.each { xobn ->

         obn = parseObjectNode(xobn, templatePath, nextArchPath, dataPath, templateDataPath)
         obn.parent = atn
         atn.children << obn
      }

      // only objects are added to the template, since some object paths colission with attr paths
      // and when getting a node, always have to return an object node, like in PATHABLE.item_at_path()
      //this.template.nodes[templatePath] = atn

      return atn
   }

   private parseCodedTerm(GPathResult node)
   {
      return new CodedTerm(
         code: node.@code.text(),
         term: parseTerm(node.items))
   }

   private parseTerm(GPathResult node)
   {
      return new Term(
         text: node.find{ it.@id == 'text' }.text(),
         description: node.find{ it.@id == 'description' }.text())
   }
}
