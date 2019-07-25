package com.cabolabs.openehr.opt

import com.cabolabs.openehr.opt.instance_validation.XmlInstanceValidation
import com.cabolabs.openehr.opt.ui_generator.OptUiGenerator
import com.cabolabs.openehr.opt.instance_generator.*
import com.cabolabs.openehr.opt.parser.*
import com.cabolabs.openehr.opt.model.*

class Main {

   private static String PS = System.getProperty("file.separator")

   /*
    * uigen // generador de ui
    * ingen // generador de instancias
    * inval // validador de instancias con XSD
    */
   static void main(String[] args)
   {
      if (args.size() == 0 || args[0] == 'help')
      {
         println 'usage: opt command [options]'
         println 'command: [uigen, ingen, inval]'
         println 'uigen: user interface generation from an OPT'
         println 'ingen: XML instance generation from an OPT'
         println 'inval: XML instance validator'
         System.exit(0)
      }

      switch (args[0])
      {
         case 'uigen':
            if (args.size() < 2)
            {
               println 'usage: opt uigen path_to_opt'
               System.exit(0)
            }

            def path = args[1] //"resources"+ PS +"opts"+ PS +"Encuentro.opt"
            def opt = loadAndParse(path)
            def gen = new OptUiGenerator()
            def ui = gen.generate(opt)

            println ui;

         break
         case 'ingen':

            //println "ingen args "+ args.size() +" "+ args // DEBUG

            if (args.size() < 3)
            {
               println 'usage: opt ingen path_to_opt [amount] [version|composition|version_committer|tagged] [withParticipations]'
               System.exit(0)
            }

            int count = 1
            if (args.size() > 3)
            {
               count = args[2].toInteger() // TOOD: check type conversion

               if (count <= 0)
               {
                  println "amount should be greater than 0"
                  System.exit(0)
               }
            }

            def path = args[1] //"resources"+ PS +"opts"+ PS +"Referral.opt"
            def opt = loadAndParse(path)

            // test
            /*
            opt.nodes.sort{ it.key }.each { p, o -> println p +' '+ o.getClass().getSimpleName() +' '+ ((o.getClass().getSimpleName() == 'AttributeNode') ? o.children.size() : '') }
            println opt.getNode('/content[archetype_id=openEHR-EHR-ACTION.test_action_multiple_occurence_node.v1]/ism_transition')
            println opt.getNode('/content[archetype_id=openEHR-EHR-ACTION.test_action_multiple_occurence_node.v1]/ism_transition[at0004]')
            */

            /*
            def destination_path = args[2]
            if (!new File(destination_path).exists())
            {
               println "destination_path $destination_path doesn't exists"
               System.exit(0)
            }
            */

            def generate = 'version'
            if (args.size() > 3)
            {
               if (!['version', 'composition', 'version_committer', 'tagged'].contains(args[3]))
               {
                  println "result type should be 'version' or 'composition' or 'version_committer' or 'tagged'"
                  System.exit(0)
               }

               generate = args[3]
            }

            def withParticipations = args.contains('withParticipations')
            //println withParticipations

            def igen, ins
            for (i in 1..count)
            {
               if (generate == 'composition')
               {
                  igen = new XmlInstanceGenerator()
                  ins = igen.generateXMLCompositionStringFromOPT(opt, withParticipations)
               }
               else if (generate == 'version')
               {
                  igen = new XmlInstanceGenerator()
                  ins = igen.generateXMLVersionStringFromOPT(opt, withParticipations)
               }
               else if (generate == 'tagged')
               {
                  igen = new XmlInstanceGeneratorTagged()
                  ins = igen.generateXMLVersionStringFromOPT(opt)
               }
               else
               {
                  igen = new XmlInstanceGeneratorForCommitter()
                  ins = igen.generateXMLVersionStringFromOPT(opt)
               }

               /*new File(args[2]).withWriter { writer ->
                  writer.write(ins)
                  writer.flush();
                  writer.close();
               }

               println "Instance generated: "*/
			   println ins;
            }
         break
         case 'inval':

            // Read XSD from JAR as a resource
            def inputStream = this.getClass().getResourceAsStream('/xsd/Versions.xsd')
            def validator = new XmlInstanceValidation(inputStream)

            if (args.size() < 2)
            {
               println 'usage: opt inval path_to_xml_instance'
               println 'usage: opt inval path_to_folder_with_xml_instances'
               System.exit(0)
            }

            def path = args[1]
            def f = new File(path)
            if (!f.exists())
            {
               println path +" doesn't exists"
               System.exit(0)
            }

            if (f.isDirectory()) // validate all the XMLs in the folder
            {
               f.eachFileMatch(~/.*.xml/) { xml ->

                 validateXMLInstance(validator, xml)
               }
            }
            else // Validate the XML instance referenced by the file
            {
               validateXMLInstance(validator, f)
            }

         break
         default:
            println "command "+ args[0] +" not recognized"
      }
   }

   static void validateXMLInstance(validator, file)
   {
      if (!validator.validate( file.text ))
      {
         println file.name +' NOT VALID'
         println '====================================='
         validator.errors.each {
            println it
         }
         println '====================================='
      }
      else
         println file.name +' VALID'
   }

   static OperationalTemplate loadAndParse(String path)
   {
      def optFile = new File( path )

      if (!optFile.exists()) throw new java.io.FileNotFoundException(path)

      def text = optFile.getText()

      assert text != null
      assert text != ''

      def parser = new OperationalTemplateParser()
      return parser.parse( text )
   }
}
