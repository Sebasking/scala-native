package salty
package ir

import salty.ir.{Tags => T, Schema => Sc}

sealed class Node(var desc: Desc, var slots: Seq[Slot]) {
  private[ir] var epoch: Int = 0

  final override def toString = {
    val name = desc.toString
    val slots = this.slots.map {
      case Var(n)     => n.toString
      case SeqVar(ns) => ns.mkString(", ")
    }.mkString("; ")
    s"$name { $slots }"
  }

  final def type_==(other: Node): Boolean =
    (this, other) match {
      case (Extern(name1), Extern(name2)) =>
        name1 == name2
      case (Type(shape1, deps1), Type(shape2, deps2)) =>
        shape1 == shape2 && deps1.zip(deps2).forall { case (l, r) => l type_== r }
      case _ =>
        this eq other
    }

  // TODO: iterator
  final def edges: Seq[(Sc, Node)] =
    desc.schema.zip(slots).flatMap {
      case (Sc.Many(sc), SeqVar(nodes)) => nodes.map { n => (sc, n) }
      case (sc         , Var(node))     => Seq((sc, node))
      case _                            => throw new Exception("schema violation")
    }
}
object Node {
  private var lastEpoch = 0
  private[ir] def nextEpoch = {
    lastEpoch += 1
    lastEpoch
  }

  def apply(desc: Desc, slots: Seq[Slot]) =
    new Node(desc, slots)
}

sealed abstract class Slot {
  def asVar = this.asInstanceOf[Var]
  def asSeqVar = this.asInstanceOf[SeqVar]
}
final case class Var(var node: Node) extends Slot {
  def :=(value: Node) = node = value
}
object Var {
  implicit def toValue(v: Var): Node = v.node
}
final case class SeqVar(var nodes: Seq[Node]) extends Slot {
  def :=(values: Seq[Node]) = nodes = values
}
object SeqVar {
  implicit def toValue(v: SeqVar): Seq[Node] = v.nodes
}

sealed abstract class Prim(name: Name) extends Node(Desc.Primitive(name), Seq())
object Prim {
  final case object Null    extends Prim(Name.Simple("null"))
  final case object Nothing extends Prim(Name.Simple("nothing"))
  final case object Unit    extends Prim(Name.Simple("unit"))
  final case object Bool    extends Prim(Name.Simple("bool"))

  sealed abstract case class I(width: Int) extends Prim(Name.Simple(s"i$width"))
  final object I8  extends I(8)
  final object I16 extends I(16)
  final object I32 extends I(32)
  final object I64 extends I(64)

  sealed abstract case class F(width: Int) extends Prim(Name.Simple(s"f$width"))
  final object F32 extends F(32)
  final object F64 extends F(64)
}

sealed abstract class Schema
object Schema {
  final case object Val              extends Schema
  final case object Cf               extends Schema
  final case object Ef               extends Schema
  final case object Ref              extends Schema
  final case class  Many(of: Schema) extends Schema
}

sealed abstract class Desc(val tag: Int, val schema: Schema*)
object Desc {
  sealed trait Plain
  sealed trait Cf
  sealed trait Termn extends Cf
  sealed trait Ef
  sealed trait Val
  sealed trait Const extends Val
  sealed trait Defn

  final case object Start             extends Desc(T.Start                              ) with Plain with Cf with Ef
  final case class  Label(name: Name) extends Desc(T.Label        , Sc.Many(Sc.Cf)      ) with Cf
  final case object If                extends Desc(T.If           , Sc.Cf, Sc.Val       ) with Plain with Cf
  final case object Switch            extends Desc(T.Switch       , Sc.Cf, Sc.Val       ) with Plain with Cf
  final case object Try               extends Desc(T.Try          , Sc.Cf               ) with Plain with Cf
  final case object CaseTrue          extends Desc(T.CaseTrue     , Sc.Cf               ) with Plain with Cf
  final case object CaseFalse         extends Desc(T.CaseFalse    , Sc.Cf               ) with Plain with Cf
  final case object CaseConst         extends Desc(T.CaseConst    , Sc.Cf, Sc.Val       ) with Plain with Cf
  final case object CaseDefault       extends Desc(T.CaseDefault  , Sc.Cf               ) with Plain with Cf
  final case object CaseException     extends Desc(T.CaseException, Sc.Cf               ) with Plain with Cf
  final case object Merge             extends Desc(T.Merge        , Sc.Many(Sc.Cf)      ) with Plain with Cf
  final case object Return            extends Desc(T.Return       , Sc.Cf, Sc.Ef, Sc.Val) with Plain with Termn
  final case object Throw             extends Desc(T.Throw        , Sc.Cf, Sc.Ef, Sc.Val) with Plain with Termn
  final case object Undefined         extends Desc(T.Undefined    , Sc.Cf, Sc.Ef        ) with Plain with Termn
  final case object End               extends Desc(T.End          , Sc.Many(Sc.Cf)      ) with Plain with Cf

  final case object EfPhi  extends Desc(T.EfPhi , Sc.Cf, Sc.Many(Sc.Ef))          with Plain with Ef
  final case object Equals extends Desc(T.Equals, Sc.Ef, Sc.Val, Sc.Val)          with Plain with Ef with Val
  final case object Call   extends Desc(T.Call  , Sc.Ef, Sc.Val, Sc.Many(Sc.Val)) with Plain with Ef with Val
  final case object Load   extends Desc(T.Load  , Sc.Ef, Sc.Val)                  with Plain with Ef with Val
  final case object Store  extends Desc(T.Store , Sc.Ef, Sc.Val, Sc.Val)          with Plain with Ef with Val

  final case object Add  extends Desc(T.Add , Sc.Val, Sc.Val) with Plain with Val
  final case object Sub  extends Desc(T.Sub , Sc.Val, Sc.Val) with Plain with Val
  final case object Mul  extends Desc(T.Mul , Sc.Val, Sc.Val) with Plain with Val
  final case object Div  extends Desc(T.Div , Sc.Val, Sc.Val) with Plain with Val
  final case object Mod  extends Desc(T.Mod , Sc.Val, Sc.Val) with Plain with Val
  final case object Shl  extends Desc(T.Shl , Sc.Val, Sc.Val) with Plain with Val
  final case object Lshr extends Desc(T.Lshr, Sc.Val, Sc.Val) with Plain with Val
  final case object Ashr extends Desc(T.Ashr, Sc.Val, Sc.Val) with Plain with Val
  final case object And  extends Desc(T.And , Sc.Val, Sc.Val) with Plain with Val
  final case object Or   extends Desc(T.Or  , Sc.Val, Sc.Val) with Plain with Val
  final case object Xor  extends Desc(T.Xor , Sc.Val, Sc.Val) with Plain with Val
  final case object Eq   extends Desc(T.Eq  , Sc.Val, Sc.Val) with Plain with Val
  final case object Neq  extends Desc(T.Neq , Sc.Val, Sc.Val) with Plain with Val
  final case object Lt   extends Desc(T.Lt  , Sc.Val, Sc.Val) with Plain with Val
  final case object Lte  extends Desc(T.Lte , Sc.Val, Sc.Val) with Plain with Val
  final case object Gt   extends Desc(T.Gt  , Sc.Val, Sc.Val) with Plain with Val
  final case object Gte  extends Desc(T.Gte , Sc.Val, Sc.Val) with Plain with Val

  final case object Trunc    extends Desc(T.Trunc   , Sc.Val, Sc.Ref) with Plain with Val
  final case object Zext     extends Desc(T.Zext    , Sc.Val, Sc.Ref) with Plain with Val
  final case object Sext     extends Desc(T.Sext    , Sc.Val, Sc.Ref) with Plain with Val
  final case object Fptrunc  extends Desc(T.Fptrunc , Sc.Val, Sc.Ref) with Plain with Val
  final case object Fpext    extends Desc(T.Fpext   , Sc.Val, Sc.Ref) with Plain with Val
  final case object Fptoui   extends Desc(T.Fptoui  , Sc.Val, Sc.Ref) with Plain with Val
  final case object Fptosi   extends Desc(T.Fptosi  , Sc.Val, Sc.Ref) with Plain with Val
  final case object Uitofp   extends Desc(T.Uitofp  , Sc.Val, Sc.Ref) with Plain with Val
  final case object Sitofp   extends Desc(T.Sitofp  , Sc.Val, Sc.Ref) with Plain with Val
  final case object Ptrtoint extends Desc(T.Ptrtoint, Sc.Val, Sc.Ref) with Plain with Val
  final case object Inttoptr extends Desc(T.Inttoptr, Sc.Val, Sc.Ref) with Plain with Val
  final case object Bitcast  extends Desc(T.Bitcast , Sc.Val, Sc.Ref) with Plain with Val
  final case object Cast     extends Desc(T.Cast    , Sc.Val, Sc.Ref) with Plain with Val
  final case object Box      extends Desc(T.Box     , Sc.Val, Sc.Ref) with Plain with Val
  final case object Unbox    extends Desc(T.Unbox   , Sc.Val, Sc.Ref) with Plain with Val

  final case object Phi                 extends Desc(T.Phi        , Sc.Cf, Sc.Many(Sc.Val)) with Plain with Val
  final case object Is                  extends Desc(T.Is         , Sc.Val, Sc.Ref        ) with Plain with Val
  final case object Alloc               extends Desc(T.Alloc      , Sc.Ref                ) with Plain with Val
  final case object Salloc              extends Desc(T.Salloc     , Sc.Ref, Sc.Val        ) with Plain with Val
  final case object Length              extends Desc(T.Length     , Sc.Val                ) with Plain with Val
  final case object Elem                extends Desc(T.Elem       , Sc.Val, Sc.Val        ) with Plain with Val
  final case class  Param(name: Name)   extends Desc(T.Param      , Sc.Ref                ) with Val
  final case object ValueOf             extends Desc(T.ValueOf    , Sc.Val                ) with Plain with Val
  final case object ExceptionOf         extends Desc(T.ExceptionOf, Sc.Cf                 ) with Plain with Val
  final case object TagOf               extends Desc(T.TagOf      , Sc.Val                ) with Plain with Val

  final case object Unit                extends Desc(T.Unit ) with Plain with Const
  final case object Null                extends Desc(T.Null ) with Plain with Const
  final case object True                extends Desc(T.True ) with Plain with Const
  final case object False               extends Desc(T.False) with Plain with Const
  final case class  I8(value: Byte)     extends Desc(T.I8   )            with Const
  final case class  I16(value: Short)   extends Desc(T.I16  )            with Const
  final case class  I32(value: Int)     extends Desc(T.I32  )            with Const
  final case class  I64(value: Long)    extends Desc(T.I64  )            with Const
  final case class  F32(value: Float)   extends Desc(T.F32  )            with Const
  final case class  F64(value: Double)  extends Desc(T.F64  )            with Const
  final case class  Str(value: String)  extends Desc(T.Str  )            with Const
  final case object Tag                 extends Desc(T.Tag  ) with Plain with Const

  final case class Class(name: Name)     extends Desc(T.Class    , Sc.Many(Sc.Ref)                                 ) with Defn
  final case class Interface(name: Name) extends Desc(T.Interface, Sc.Many(Sc.Ref)                                 ) with Defn
  final case class Module(name: Name)    extends Desc(T.Module   , Sc.Many(Sc.Ref)                                 ) with Defn
  final case class Declare(name: Name)   extends Desc(T.Declare  , Sc.Ref, Sc.Many(Sc.Val), Sc.Many(Sc.Ref)        ) with Defn
  final case class Define(name: Name)    extends Desc(T.Define   , Sc.Ref, Sc.Many(Sc.Val), Sc.Val, Sc.Many(Sc.Ref)) with Defn
  final case class Field(name: Name)     extends Desc(T.Field    , Sc.Ref, Sc.Many(Sc.Ref)                         ) with Defn
  final case class Extern(name: Name)    extends Desc(T.Extern                                                     ) with Defn
  final case class Type(shape: Shape)    extends Desc(T.Type     , Sc.Many(Sc.Ref)                                 ) with Defn
  final case class Primitive(name: Name) extends Desc(T.Primitive                                                  ) with Defn
}

sealed abstract class Name {
  final override def toString = this match {
    case Name.No           => ""
    case Name.Simple(id)   => id
    case Name.Nested(l, r) => s"$l::$r"
  }
}
object Name {
  final case object No extends Name
  final case class Simple(id: String) extends Name
  final case class Nested(parent: Name, child: Name) extends Name
}

sealed abstract class Shape {
  final def holes: Int = this match {
    case Shape.Hole         => 1
    case Shape.Ref(shape)   => shape.holes
    case Shape.Slice(shape) => shape.holes
  }

  final override def toString = this match {
    case Shape.Hole         => "•"
    case Shape.Ref(shape)   => s"$shape!"
    case Shape.Slice(shape) => s"$shape[]"
  }
}
object Shape {
  final case object Hole extends Shape
  final case class Ref(of: Shape) extends Shape
  final case class Slice(of: Shape) extends Shape
  // TODO: Func(ret, args)
  // TODO: Struct(fields)
  // TODO: Array(t, n)
}

final case class Scope(entries: Map[Name, Node])
