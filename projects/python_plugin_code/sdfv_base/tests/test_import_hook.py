import json
import textwrap


def test_importer_can_resolve_registered_plugin_modules():
    # generate dumps
    dump_package_a = json.dumps({
        "cms_rendner_sdfv": {
            "package_a": {
                "class_a": textwrap.dedent("""
                    from cms_rendner_sdfv.package_b.class_b import ClassB
                    class ClassA:
                        def b(self):
                            return ClassB()
                    
                        def type(self):
                            return type(self)
                    """)
            },
        }
    })
    dump_package_b = json.dumps({
        "cms_rendner_sdfv": {
            "package_b": {
                "class_b": textwrap.dedent("""
                        class ClassB:
                            def type(self):
                                return type(self)
                        """)
            },
        }
    })

    # required to register the importer
    # noinspection PyUnresolvedReferences
    import importer.plugin_modules_importer

    # register dumps
    from cms_rendner_sdfv import package_registry
    package_registry.register_package_dump("dump_package_a", dump_package_a)
    package_registry.register_package_dump("dump_package_b", dump_package_b)

    # verify registered dumps
    registered_dumbs = package_registry.get_registered_dump_ids()
    assert registered_dumbs == ["dump_package_a", "dump_package_b"]

    # use classes from the registered dumps
    from cms_rendner_sdfv.package_a.class_a import ClassA
    from cms_rendner_sdfv.package_b.class_b import ClassB

    a = ClassA()
    assert a.type() == type(a)
    assert isinstance(a.b(), ClassB)
