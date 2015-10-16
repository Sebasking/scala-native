package salty.ir
package serialization

import scala.collection.mutable
import java.nio.ByteBuffer
import salty.ir.{Desc => D, Tags => T}

class SaltySerializer(buffer: ByteBuffer) {
  import buffer._

  private val offsets  = mutable.Map[Node, Int]()
  private val worklist = mutable.Stack[(Int, Node)]()

  def serialize(scope: Scope) = {
    putInt(scope.entries.size)
    scope.entries.foreach {
      case (n, node) =>
        putName(n)
        worklist.push(position -> node)
        putInt(0)
    }
    while (worklist.nonEmpty) {
      val (pos, node) = worklist.pop()
      val offset =
        if (offsets.contains(node))
          offsets(node)
        else
          putNode(node)
      mark
      putInt(pos, offset)
      reset
    }
  }

  private def putNode(n: Node): Int =  {
    val pos = position
    offsets += (n -> pos)
    if (n eq Empty)
      putDesc(D.Empty)
    else {
      putDesc(n.desc)
      putName(n.name)
      if (n.desc.schema.nonEmpty) {
        putSeq(n.offsets)(putInt(_))
        putSeq(n.slots)(putSlot(_))
      }
    }
    pos
  }

  private def putDesc(desc: Desc) = desc match {
    case plain: D.Plain => putInt(T.plain2tag(plain))
    case D.I8(v)        => putInt(T.I8); put(v)
    case D.I16(v)       => putInt(T.I16); putShort(v)
    case D.I32(v)       => putInt(T.I32); putInt(v)
    case D.I64(v)       => putInt(T.I64); putLong(v)
    case D.F32(v)       => putInt(T.F32); putFloat(v)
    case D.F64(v)       => putInt(T.F64); putDouble(v)
    case D.Str(v)       => putInt(T.Str); putString(v)
    case D.Type(shape)  => putInt(T.Type); putShape(shape)
  }

  private def putSeq[T](seq: Seq[T])(putT: T => Unit) = {
    putInt(seq.length)
    seq.foreach(putT)
  }

  private def putSlot(slot: Slot) = putNodeRef(slot.get)

  private def putNodeRef(n: Node) =
    if (offsets.contains(n))
      putInt(offsets(n))
    else {
      worklist.push(position -> n)
      putInt(0)
    }

  private def putName(name: Name): Unit = name match {
    case Name.No =>
      putInt(T.NoName)
    case Name.Local(v) =>
      putInt(T.LocalName); putString(v)
    case Name.Class(v) =>
      putInt(T.ClassName); putString(v)
    case Name.Module(v) =>
      putInt(T.ModuleName); putString(v)
    case Name.Interface(v) =>
      putInt(T.InterfaceName); putString(v)
    case Name.Primitive(v) =>
      putInt(T.PrimitiveName); putString(v)
    case Name.Slice(n) =>
      putInt(T.SliceName); putName(n)
    case Name.Field(owner, id) =>
      putInt(T.FieldName); putName(owner); putString(id)
    case Name.Constructor(owner, params) =>
      putInt(T.ConstructorName); putName(owner); putSeq(params)(putName)
    case Name.Method(owner, id, params, ret) =>
      putInt(T.MethodName); putName(owner); putString(id); putSeq(params)(putName); putName(ret)
  }

  private def putShape(shape: Shape): Unit = shape match {
    case Shape.Hole     => putInt(T.HoleShape)
    case Shape.Ref(s)   => putInt(T.RefShape); putShape(s)
    case Shape.Slice(s) => putInt(T.SliceShape); putShape(s)
  }

  private def putString(v: String) = {
    val bytes = v.getBytes
    putInt(bytes.length); put(bytes)
  }
}