"""Artifact boundary tests: reject broken resources; repack editable art without regeneration."""
import hashlib, importlib.util, json, shutil, tempfile, unittest, zipfile
from pathlib import Path

spec=importlib.util.spec_from_file_location("bf_pack",Path(__file__).with_name("generate_expansion_pack.py"))
pack=importlib.util.module_from_spec(spec);spec.loader.exec_module(pack)

class PackArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.oldpack,self.oldout=pack.PACK,pack.OUT
        pack.PACK=self.root/"source";pack.OUT=self.root/"output";shutil.copytree(self.oldpack,pack.PACK)
        self.client=zipfile.ZipFile(pack.CLIENT)
    def tearDown(self):
        self.client.close();pack.PACK,self.temp_pack=self.oldpack,pack.PACK;pack.OUT=self.oldout;self.temp.cleanup()
    def test_repack_preserves_manually_edited_source_and_refreshes_hash(self):
        metadata=json.loads((pack.PACK/"pack.mcmeta").read_text(encoding="utf-8"));metadata["pack"]["description"]="Hand-edited source preserved"
        (pack.PACK/"pack.mcmeta").write_text(json.dumps(metadata),encoding="utf-8")
        art=pack.PACK/"assets/blastforge/textures/item/legendary_scythe_art.png";before=hashlib.sha1(art.read_bytes()).hexdigest()
        result=pack.repack(self.client,pack.catalog())
        with zipfile.ZipFile(result["zip"]) as archive:
            self.assertEqual("Hand-edited source preserved",json.loads(archive.read("pack.mcmeta"))["pack"]["description"])
            self.assertEqual(before,hashlib.sha1(archive.read("assets/blastforge/textures/item/legendary_scythe_art.png")).hexdigest())
        self.assertEqual(before,hashlib.sha1(art.read_bytes()).hexdigest())
        self.assertEqual(hashlib.sha1(Path(result["zip"]).read_bytes()).hexdigest(),result["sha1"])
    def test_broken_texture_reference_is_rejected(self):
        path=pack.PACK/"assets/blastforge/models/item/siftie_sword.json";model=json.loads(path.read_text());model["textures"]["layer0"]="blastforge:item/not_present";path.write_text(json.dumps(model))
        with self.assertRaisesRegex(AssertionError,"Missing texture"):
            pack.validate(self.client,pack.catalog())

if __name__=="__main__":unittest.main()
