package fix

import scalafix._
import scalafix.syntax._
import scala.meta._

case class Collectionstrawman_v0(sctx: SemanticCtx)
    extends SemanticRewrite(sctx) {
  val immutableListSymbol = Symbol("_root_.scala.collection.immutable.List.")
  implicit class XtensionSymbolCollection(symbol: Symbol) {
    def name = symbol match {
      case Symbol.Global(_, sig) => sig.name
      case _                     => symbol.syntax
    }
  }

  val unimports = Map(
    Symbol("_root_.scala.Predef.augmentString.") ->
      Symbol("_root_.strawman.collection.stringToStringOps."),
    Symbol("_root_.scala.Predef.intArrayOps.") ->
      Symbol("_root_.strawman.collection.arrayToArrayOps.")
  )

  def ifSymbolFound(ctx: RewriteCtx): Patch = {
    val toImport = for {
      r <- ctx.semanticCtx.names
      in = r.sym.normalized
      out <- unimports.get(in).toList
    } yield {
      ctx.addGlobalImport(out) +
        ctx.addGlobalImport(
          Importer(q"scala.Predef", Importee.Unimport(Name(in.name)) :: Nil))
    }
    val predefUnderscore =
      if (toImport.isEmpty) Patch.empty
      else ctx.addGlobalImport(importer"scala.Predef._")
    toImport.asPatch + predefUnderscore
  }

  val rangeImport = Symbol("_root_.strawman.collection.immutable.Range.")
  val inclusiveRange = Symbol(
    "_root_.scala.runtime.RichInt#to(I)Lscala/collection/immutable/Range/Inclusive;.")
  val rangeSymbol = Symbol(
    "_root_.scala.runtime.RichInt#until(I)Lscala/collection/immutable/Range;.")
  def range(ctx: RewriteCtx): Patch = {
    ctx.tree.collect {
      case tree @ Term.ApplyInfix(lhs, op, targs, arg :: Nil)
          if op.symbol.contains(inclusiveRange) =>
        ctx.replaceTree(tree, q"Range.inclusive($lhs, $arg)".syntax) +
          ctx.addGlobalImport(rangeImport)
      case tree @ Term.ApplyInfix(lhs, op, targs, arg :: Nil)
          if op.symbol.contains(rangeSymbol) =>
        ctx.replaceTree(tree, q"Range($lhs, $arg)".syntax) +
          ctx.addGlobalImport(rangeImport)
    }
  }.asPatch

  def rewrite(ctx: RewriteCtx): Patch = {
    def p(name: String) =
      s"scala.Predef.$name" -> s"strawman.collection.immutable.$name"
    def s(name: String, rename: Option[String] = None) =
      s"scala.$name" -> s"strawman.collection.immutable.${rename.getOrElse(name)}"
    def i(name: String, rename: Option[String] = None) =
      s"scala.collection.immutable.$name" ->
        s"strawman.collection.immutable.${rename.getOrElse(name)}"
    def m(name: String) =
      s"scala.collection.mutable.$name" -> s"strawman.collection.mutable.$name"
    ctx.replaceSymbols(
      s("Set"),
      i("HashMap"),
      i("Map"),
      p("Map"),
      s("List"),
      i("List"),
      s("Nil"),
      i("Nil"),
      s("`::`"),
      i("`::`"),
      s("`+:`"),
      i("`+:`"),
      s("`:+`"),
      i("`:+`"),
      i("Stream", Some("LazyList")),
      s("Stream", Some("LazyList")),
      s("Traversable", Some("Iterable")),
      "scala.Iterable" -> "strawman.collection.Iterable",
      "scala.Traversable" -> "strawman.collection.Iterable",
      "scala.`#::`" -> "strawman.collection.immutable.LazyList.`#::`",
      s("Vector"),
      i("Vector"),
      m("ArrayBuffer")
    ) +
      ifSymbolFound(ctx) +
      range(ctx)
  }
}
