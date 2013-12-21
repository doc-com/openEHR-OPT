package com.cabolabs.openehr.opt.parser

import com.cabolabs.openehr.opt.model.*
import groovy.util.slurpersupport.GPathResult

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
      
      parseOperationalTemplate(templateXML)
   }
   
   private parseOperationalTemplate(GPathResult tpXML)
   {
      this.template = new OperationalTemplate(
         uid: tpXML.uid.value.text(),
         templateId: tpXML.template_id.value.text(),
         concept: tpXML.concept.text(),
         language: parseCodePhrase(tpXML.language),
         isControlled: ((!tpXML.is_controlled.isEmpty() && tpXML.is_controlled.text() != '') ? tpXML.is_controlled.text() : false),
         purpose: tpXML.description.details.purpose.text(),
         // TODO: add use, misuse, keywords, etc. from RESOURCE_DESCRIPTION_ITEM: https://github.com/openEHR/java-libs/blob/f6ee434226bf926d261c2690016c1d6022b877be/oet-parser/src/main/xsd/Resource.xsd
         definition: parseObjectNode(tpXML.definition, '/')
      )
   }
   
   private parseCodePhrase(GPathResult node)
   {
      return node.terminology_id.value.text() +"::"+ node.code_string.text()
   }
   
   private parseObjectNode(GPathResult node, String parentPath)
   {
      // Path calculation
      def path = parentPath
      if (path != '/')
      {
         // comienza de nuevo con las paths relativas al root de este arquetipo
         if (!node.archetype_id.value.isEmpty())
         {
            //println ""
            //println "archid: "+ node.archetype_id.value
            //parentPath = '/'
            path += '[archetype_id='+ node.archetype_id.value +']' // slot in the path instead of node_id
         }
         // para tag vacia empty da false pero text es vacio ej. <node_id/>
         else if (!node.node_id.isEmpty() && node.node_id.text() != '')
         {
            path += '['+ node.node_id.text() + ']'
         }
      }
      
      def obn = new ObjectNode(
         rmTypeName: node.rm_type_name.text(),
         nodeId: node.node_id.text(),
         type: node.'@xsi:type'.text(),
         archetypeId: node.archetype_id.value.text(), // This is optional, just resolved slots have archId
         path: path
         // TODO: default_values
      )
      
      // TODO: parse occurrences
      
      node.attributes.each { xatn ->
      
         obn.attributes << parseAttributeNode(xatn, path)
      }
      
      return obn
   }
   
   private parseAttributeNode(GPathResult node, String parentPath)
   {
      // Path calculation
      def path = parentPath
      if (path == '/') path += node.rm_attribute_name.text() // Avoids to repeat '/'
      else path += '/'+ node.rm_attribute_name.text()
      
      
      def atn = new AttributeNode(
         rmAttributeName: node.rm_attribute_name.text(),
         type: node.'@xsi:type'.text()
         // TODO: cardinality
         // TODO: existence
      )
      
      node.children.each { xobn ->
      
         atn.children << parseObjectNode(xobn, path)
      }
      
      return atn
   }
   
   private parseCodedTerm(GPathResult node)
   {
   }
   
   private parseTerm(GPathResult node)
   {
   }
}