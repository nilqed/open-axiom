-- Copyright (c) 1991-2002, The Numerical ALgorithms Group Ltd.
-- All rights reserved.
-- Copyright (C) 2007-2010, Gabriel Dos Reis.
-- All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are
-- met:
--
--     - Redistributions of source code must retain the above copyright
--       notice, this list of conditions and the following disclaimer.
--
--     - Redistributions in binary form must reproduce the above copyright
--       notice, this list of conditions and the following disclaimer in
--       the documentation and/or other materials provided with the
--       distribution.
--
--     - Neither the name of The Numerical ALgorithms Group Ltd. nor the
--       names of its contributors may be used to endorse or promote products
--       derived from this software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
-- IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
-- TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
-- PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
-- OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
-- EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
-- PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
-- PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
-- LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
-- NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
-- SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


import i_-analy
import i_-resolv
namespace BOOT

$useCoerceOrCroak := true
$useConvertForCoercions := false

--%  Algebraic coercions using interactive code

algCoerceInteractive(p,source,target) ==
  -- now called in some groebner code
  $useConvertForCoercions : local := true
  source := devaluate source
  target := devaluate target
  u := coerceInteractive(objNewWrap(p,source),target)
  u => objValUnwrap(u)
  error ['"can't convert",p,'"of mode",source,'"to mode",target]

spad2BootCoerce(x,source,target) ==
  -- x : source and we wish to coerce to target
  -- used in spad code for Any
  null isValidType source => throwKeyedMsg("S2IE0004",[source])
  null isValidType target => throwKeyedMsg("S2IE0004",[target])
  x' := coerceInteractive(objNewWrap(x,source),target) =>
    objValUnwrap(x')
  throwKeyedMsgCannotCoerceWithValue(wrap x,source,target)

--%  Functions for Coercion or Else We'll Get Rough

coerceOrFail(triple,t,mapName) ==
  -- some code generated for this is in coerceInt0
  t = $NoValueMode => triple
  t' := coerceInteractive(triple,t)
  t' => objValUnwrap(t')
  sayKeyedMsg("S2IC0004",[mapName,objMode triple,t])
  '"failed"

coerceOrCroak(triple, t, mapName) ==
  -- this does the coercion and returns the value or dies
  t = $NoValueMode => triple
  t' := coerceOrConvertOrRetract(triple,t)
  t' => objValUnwrap(t')
  mapName = 'noMapName =>
    throwKeyedMsgCannotCoerceWithValue(objVal triple,objMode triple, t)
  sayKeyedMsg("S2IC0005",[mapName])
  throwKeyedMsgCannotCoerceWithValue(objVal triple,objMode triple, t)

coerceOrThrowFailure(value, t1, t2) ==
  (result := coerceOrRetract(objNewWrap(value, t1), t2)) or
    coercionFailure()
  objValUnwrap(result)

--%  Retraction functions

retract object ==
  type := objMode object
  string? type => 'failed
  type = $EmptyMode => 'failed
  val := objVal object
  not isWrapped val and val isnt ["%Map",:.] => 'failed
  type' := equiType(type)
  (ans := retract1 objNew(val,equiType(type))) = 'failed => ans
  objNew(objVal ans,eqType objMode ans)

retract1 object ==
  -- this function is the new version of the old "pullback"
  -- it first tries to change the datatype of an object to that of
  -- largest contained type. Examples: P RN -> RN, RN -> I
  -- This is mostly for cases such as constant polynomials or
  -- quotients with 1 in the denominator.
  type := objMode object
  string? type => 'failed
  val := objVal object
  type = $PositiveInteger =>    objNew(val,$NonNegativeInteger)
  type = $NonNegativeInteger => objNew(val,$Integer)
  type = $Integer and SINTP unwrap val => objNew(val, $SingleInteger)
  type' := equiType(type)
  if not EQ(type,type') then object := objNew(val,type')
  (1 = #type') or (type' is ['Union,:.]) or
    (type' is ['FunctionCalled,.])
     or (type' is ['OrderedVariableList,.]) or (type is ['Variable,.]) =>
      (object' := retract2Specialization(object)) => object'
      'failed
  null (underDomain := underDomainOf type') => 'failed
  -- try to retract the "coefficients"
  -- think of P RN -> P I or M RN -> M I
  object' := retractUnderDomain(object,type,underDomain)
  object' ~= 'failed => object'
  -- see if we can use the retract functions
  (object' := coerceRetract(object,underDomain)) => object'
  -- see if we have a special case here
  (object' := retract2Specialization(object)) => object'
  'failed

retractUnderDomain(object,type,underDomain) ==
  null (ud := underDomainOf underDomain) => 'failed
  [c,:args] := deconstructT type
  1 ~= #args => 'failed
  1 ~= #c => 'failed
  type'' := constructT(c,[ud])
  (object' := coerceInt(object,type'')) => object'
  'failed

retract2Specialization object ==
  -- handles some specialization retraction cases, like matrices
  val := objVal object
  val' := unwrap val
  type := objMode object

  type = $Any =>
    [dom,:obj] := val'
    objNewWrap(obj,dom)
  type is ['Union,:unionDoms] => coerceUnion2Branch object
  type = $Symbol =>
    objNewWrap(1,['OrderedVariableList,[val']])
  type is ['OrderedVariableList,var] =>
    coerceInt(objNewWrap(var.(val'-1),$Symbol), '(Polynomial (Integer)))
-- !! following retract seems wrong and breaks ug13.input
--  type is ['Variable,var] =>
--    coerceInt(object,$Symbol)
  type is ['Polynomial,D] =>
    val' is [ =1,x,:.] =>
      vl := removeDuplicates reverse varsInPoly val'
      1 = #vl => coerceInt(object,['UnivariatePolynomial,x,D])
      NIL
    val' is [ =0,:.] => coerceInt(object, D)
    NIL
  type is ['Matrix,D] =>
    n := # val'
    m := # val'.0
    n = m => objNew(val,['SquareMatrix,n,D])
    objNew(val,['RectangularMatrix,n,m,D])
  type is ['RectangularMatrix,n,m,D] =>
    n = m => objNew(val,['SquareMatrix,n,D])
    NIL
  (type is [agg,D]) and (agg in '(Vector Segment UniversalSegment)) =>
    D = $PositiveInteger => objNew(val,[agg,$NonNegativeInteger])
    D = $NonNegativeInteger => objNew(val,[agg,$Integer])
    NIL
  type is ['Array,bds,D] =>
    D = $PositiveInteger => objNew(val,['Array,bds,$NonNegativeInteger])
    D = $NonNegativeInteger => objNew(val,['Array,bds,$Integer])
    NIL
  type is ['List,D] =>
    D isnt ['List,D'] =>
      -- try to retract elements
      D = $PositiveInteger => objNew(val,['List,$NonNegativeInteger])
      D = $NonNegativeInteger => objNew(val,['List,$Integer])
      null val' => nil
--        null (um := underDomainOf D) => nil
--        objNewWrap(nil,['List,um])
      vl := nil
      tl := nil
      bad := nil
      for e in val' while not bad repeat
        (e' := retract objNewWrap(e,D)) = 'failed => bad := true
        vl := [objValUnwrap e',:vl]
        tl := [objMode e',:tl]
      bad => NIL
      (m := resolveTypeListAny tl) = D => NIL
      D = equiType(m) => NIL
      vl' := nil
      for e in vl for t in tl repeat
        t = m => vl' := [e,:vl']
        e' := coerceInt(objNewWrap(e,t),m)
        null e' => return NIL
        vl' := [objValUnwrap e',:vl']
      objNewWrap(vl',['List,m])
    D' = $PositiveInteger =>
      objNew(val,['List,['List,$NonNegativeInteger]])
    D' = $NonNegativeInteger =>
      objNew(val,['List,['List,$Integer]])
    D' is ['Variable,.] or D' is ['OrderedVariableList,.] =>
        coerceInt(object,['List,['List,$Symbol]])

    n := # val'
    m := # val'.0
    null isRectangularList(val',n,m) => NIL
    coerceInt(object,['Matrix,D'])
  type is ['Expression,D] =>
    atom val' => nil          -- certainly not a fraction
    [num,:den] := val'
    ofCategory(type,$Field) =>
      -- coerceRetract already handles case where den = 1
      num isnt [0,:num] => NIL
      den isnt [0,:den] => NIL
      objNewWrap([num,:den],[$QuotientField, D])
    nil
  type is ['SimpleAlgebraicExtension,k,rep,.] =>
    -- try to retract as an element of rep and see if we can get an
    -- element of k
    val' := retract objNew(val,rep)
    while (val' ~= 'failed) and
      (equiType(objMode val') ~= k) repeat
        val' := retract val'
    val' = 'failed => NIL
    val'

  type is ['UnivariatePuiseuxSeries, coef, var, cen] =>
    coerceInt(object, ['UnivariateLaurentSeries, coef, var, cen])
  type is ['UnivariateLaurentSeries, coef, var, cen] =>
    coerceInt(object, ['UnivariateTaylorSeries, coef, var, cen])

  type is ['FunctionCalled,name] =>
    null (m := get(name,'mode,$e)) => NIL
    isPartialMode m => NIL
    objNew(val,m)
  NIL

coerceOrConvertOrRetract(T,m) ==
  $useConvertForCoercions : local := true
  coerceOrRetract(T,m)

coerceOrRetract(T,m) ==
  (t' := coerceInteractive(T,m)) => t'
  t := T
  ans := nil
  repeat
    ans => return ans
    t := retract t   -- retract is new name for pullback
    t = 'failed => return ans
    ans := coerceInteractive(t,m)
  ans

coerceRetract(object,t2) ==
  -- tries to handle cases such as P I -> I
  (val := objValUnwrap(object)) = "$fromCoerceable$" => NIL
  t1 := objMode object
  t2 = $OutputForm => NIL
  isEqualOrSubDomain(t1,$Integer) and typeIsASmallInteger(t2) and SMINTP(val) =>
    objNewWrap(val,t2)
  t1 = $Integer    => NIL
  t1 = $Symbol     => NIL
  t1 = $OutputForm => NIL
  (c := retractByFunction(object, t2)) => c
  t1 is [D,:.] =>
    fun := GETL(D,'retract) or
           INTERN strconc('"retract",STRINGIMAGE D)
    functionp fun =>
      PUT(D,'retract,fun)
      c := CATCH('coerceFailure,FUNCALL(fun,object,t2))
      (c = $coerceFailure) => NIL
      c
    NIL
  NIL

retractByFunction(object,u) ==
  -- tries to retract by using function "retractIfCan"
  -- if the type belongs to the correct category.
  $reportBottomUpFlag: local := NIL
  t := objMode object
  -- JHD/CRF not ofCategory(t,['RetractableTo,u]) => NIL
  val := objValUnwrap object

  -- try to get and apply the function "retractable?"
  target := ['Union,u,'"failed"]
  funName := 'retractIfCan
  if $reportBottomUpFlag then
    sayFunctionSelection(funName,[t],target,NIL,
      '"coercion facility (retraction)")
  -- JHD/CRF if (mms := findFunctionInDomain(funName,t,target,[t],[t],'T,'T))
  -- MCD: changed penultimate variable to NIL.
  if (mms := append(findFunctionInDomain(funName,t,target,[t],[t],NIL,'T),
                    findFunctionInDomain(funName,u,target,[t],[t],NIL,'T)))
-- The above two lines were:      (RDJ/BMT 6/95)
--  if (mms := append(findFunctionInDomain(funName,t,target,[t],[t],'T,'T),
--                    findFunctionInDomain(funName,u,target,[t],[t],'T,'T)))
    then mms := orderMms(funName,mms,[t],[t],target)
  if $reportBottomUpFlag then
    sayFunctionSelectionResult(funName,[t],mms)
  null mms => NIL

  -- [[dc,:.],slot,.]:= first mms
  dc := CAAAR mms
  slot := CADAR mms
  dcVector:= evalDomain dc
  fun :=
--+
    compiledLookup(funName,[target,t],dcVector)
  null fun => NIL
  first(fun) = function Undef => NIL
--+
  $: fluid := dcVector
  object' := coerceUnion2Branch objNewWrap(SPADCALL(val,fun),target)
  u' := objMode object'
  u = u' => object'
  NIL

--% Coercion utilities

-- The next function extracts the structural definition of constants
-- from a given domain. For example, getConstantFromDomain('(One),S)
-- returns the representation of 1 in the domain S.

getConstantFromDomain(form,domainForm) ==
    isPartialMode domainForm => NIL
    opAlist := getOperationAlistFromLisplib first domainForm
    key := opOf form
    entryList := LASSOC(key,opAlist)
    entryList isnt [[sig, ., ., .]] =>
        key = "One" => getConstantFromDomain(["1"], domainForm)
        key = "Zero" => getConstantFromDomain(["0"], domainForm)
        throwKeyedMsg("S2IC0008",[form,domainForm])
    -- i.e., there should be exactly one item under this key of that form
    domain := evalDomain domainForm
    SPADCALL compiledLookupCheck(key,sig,domain)


domainOne(domain) == getConstantFromDomain('(One),domain)

domainZero(domain) == getConstantFromDomain('(Zero),domain)

equalOne(object, domain) ==
  -- tries using constant One and "=" from domain
  -- object should not be wrapped
  algEqual(object, getConstantFromDomain('(One),domain), domain)

equalZero(object, domain) ==
  -- tries using constant Zero and "=" from domain
  -- object should not be wrapped
  algEqual(object, getConstantFromDomain('(Zero),domain), domain)

algEqual(object1, object2, domain) ==
  -- sees if 2 objects of the same domain are equal by using the
  -- "=" from the domain
  -- objects should not be wrapped
--  eqfunc := getFunctionFromDomain("=",domain,[domain,domain])
  eqfunc := compiledLookupCheck("=",[$Boolean,'$,'$],evalDomain domain)
  SPADCALL(object1,object2, eqfunc)

--%  main algorithms for canCoerceFrom and coerceInteractive

-- coerceInteractive and canCoerceFrom are the two coercion functions
-- for $InteractiveMode. They translate RN, RF and RR to QF I, QF P
-- and RE RN, respectively, and call coerceInt or canCoerce, which
-- both work in the same way (e.g. coercion from t1 to t2):

-- 1. they try to coerce t1 to t2 directly (tower coercion), and, if
--   this fails, to coerce t1 to the last argument of t2 and embed
--   this last argument into t2. These embedding functions are now only
--   defined in the algebra code. (RSS 2-27-87)

-- 2. the tower coercion looks whether there is any applicable local
--   coercion, which means, one defined in boot or in algebra code.
--   If there is an applicable function from a constructor, which is
--   inside the type tower of t1, to the top level constructor of t2,
--   then this constructor is bubbled up inside t1. This means,
--   special coercion functions (defined in boot) are called, which
--   commute two constructors in a tower. Then the local coercion is
--   called on these constructors, which both are on top level now.

-- example:
-- let t1 = A B C D E (short for (A (B (C (D (E))))), where A ... E are
--   type constructors), and t2 = F D G H I J
-- there is no coercion from t1 to t2 directly, so we try to coerce
--   t1 to s1 = D G H I J, the last argument of t2
-- we create the type s2 = A D B C E and call a local coercion A2A
--   from t1 to s2, which, by recursively calling coerce, bubbles up
--   the constructor D
-- then we call a commute coerce from s2 to s3 = D A B C E and a local
--   coerce D2D from s3 to s1
-- finally we embed s1 into t2, which completes the coercion t1 to t2

-- the result of canCoerceFrom is TRUE or NIL
-- the result of coerceInteractive is a object or NIL (=failed)
-- all boot coercion functions have the following result:
-- 1. if u=$fromCoerceable$, then TRUE or NIL
-- 2. if the coercion succeeds, the coerced value (this may be NIL)
-- 3. if the coercion fails, they throw to a catch point in
--      coerceByFunction

--% Interpreter Coercion Query Functions

canCoerce1(t1,t2) ==
  -- general test for coercion
  -- the result is NIL if it fails
  t1 = t2 => true
  absolutelyCanCoerceByCheating(t1,t2) or t1 = $None or t2 = $Any or
    member(t1,'((Mode) (Category))) =>
      t2 = $OutputForm => true
      NIL
    -- next is for tagged union selectors for the time being
    t1 is ['Variable,=t2] or t2 is ['Variable,=t1] => true
    string? t1 =>
      t2 = $String => true
      t2 = $OutputForm => true
      t2 is ['Union,:.] => canCoerceUnion(t1,t2)
      t2 is ['Variable,v] and (t1 = PNAME(v)) => true
      NIL
    string? t2 =>
      t1 is ['Variable,v] and (t2 = PNAME(v)) => true
      NIL
    atom t1 or atom t2 => NIL
    null isValidType(t2) => NIL

    absolutelyCannotCoerce(t1,t2) => NIL

    nt1 := first t1
    nt2 := first t2

    nt1="Mapping" => nt2="Any"
    nt2="Mapping" =>
      nt1="Variable" or nt1="FunctionCalled" =>
        canCoerceExplicit2Mapping(t1,t2)
      NIL
    nt1="Union" or nt2="Union" => canCoerceUnion(t1,t2)

    -- efficiency hack
    t1 is ['Segment, s1] and t2 is ['UniversalSegment, s2] and
        (isEqualOrSubDomain(s1, s2) or canCoerce(s1, s2)) => true

    t1 is ['Tuple,S] and t2 ~= '(OutputForm) => canCoerce(['List, S], t2)

    isRingT2 := ofCategory(t2,'(Ring))
    isRingT2 and isEqualOrSubDomain(t1,$Integer) => true
    (ans := canCoerceTopMatching(t1,t2,nt1,nt2)) ~= 'maybe => ans
    t2 = $Integer => canCoerceLocal(t1,t2)   -- is true
    ans := canCoerceTower(t1,t2) or
      [.,:arg]:= deconstructT t2
      arg and
        t:= last arg
        canCoerce(t1,t) and canCoerceByFunction(t,t2) and 'T
    ans or member(t1,'((PositiveInteger) (NonNegativeInteger)))
      and canCoerce($Integer,t2)

canCoerceFrom0(t1,t2) ==
-- top level test for coercion, which transfers all RN, RF and RR into
-- equivalent types
  startTimingProcess 'querycoerce
  q :=
    isEqualOrSubDomain(t1,t2) or t1 = $None or t2 = $Any or
      if t2 = $OutputForm then (s1 := t1; s2 := t2)
      else (s1:= equiType(t1); s2:= equiType(t2))

      -- make sure we are trying to coerce to a legal type
      -- in particular, polynomials are repeated, etc.
      null isValidType(t2) => NIL
      null isLegitimateMode(t2,nil,nil) => NIL

      t1 = $RationalNumber =>
        isEqualOrSubDomain(t2,$Integer) => NIL
        canCoerce(t1,t2) or canCoerce(s1,s2)
      canCoerce(s1,s2)
  stopTimingProcess 'querycoerce
  q

isSubTowerOf(t1,t2) ==
  -- assumes RF and RN stuff has been expanded
  -- tests whether t1 is somewhere inside t2
  isEqualOrSubDomain(t1,t2) => true
  null (u := underDomainOf t2) => nil
  isSubTowerOf(t1,u)

canCoerceTopMatching(t1,t2,tt1,tt2) ==
  -- returns true, nil or maybe
  -- for example, if t1 = P[x] D1 and t2 = P[y] D2 and x = y then
  -- canCoerce will only be true if D1 = D2
  not EQ(tt1,tt2) => 'maybe
  doms := '(Polynomial List Matrix FiniteSet Vector Stream Gaussian)
  MEMQ(tt1,doms) => canCoerce(second t1, second t2)
  not (MEMQ(tt1,$univariateDomains) or MEMQ(tt2,$multivariateDomains)) =>
    'maybe
  u2 := deconstructT t2
  1 = #u2 => NIL
  u1 := deconstructT t1
  1 = #u1 => NIL                             -- no under domain
  first(u1) ~= first(u2) => 'maybe
  canCoerce(underDomainOf t1, underDomainOf t2)

canCoerceExplicit2Mapping(t1,t is ['Mapping,target,:argl]) ==
  -- determines if there a mapping called var with the given args
  -- and target
  $useCoerceOrCroak: local := nil
  t1 is ['Variable,var] =>
    null (mms :=selectMms1(var,target,argl,[NIL for a in argl],true)) => NIL
    mm := CAAR mms
    mm is [., targ, :.] =>
      targ = target => true
      false
    false
  t1 is ['FunctionCalled,fun] =>
    funNode := mkAtreeNode fun
    transferPropsToNode(fun,funNode)
    mms := CATCH('coerceOrCroaker, selectLocalMms(funNode,fun,argl,target))
    cons? mms =>
      mms is [[['interpOnly,:.],:.]] => nil
      mm := CAAR mms
      mm is [., targ, :.] =>
        targ = target => true
        false
      false
    NIL
  NIL

canCoerceUnion(t1,t2) ==
  -- sees if one can coerce to or from a Union Domain
  -- assumes one of t1 and t2 is one

  -- get the domains in the union, checking for tagged unions
  if (isUnion1 := t1 is ['Union,:uds1]) then
    unionDoms1 :=
      uds1 and first uds1 is [":",:.] => [t for [.,.,t] in uds1]
      uds1
  if (isUnion2 := t2 is ['Union,:uds2]) then
    unionDoms2 :=
      uds2 and first uds2 is [":",:.] => [t for [.,.,t] in uds2]
      uds2

  isUnion2 =>
    member(t1,unionDoms2) => true
    isUnion1 =>
      and/[or/[canCoerce(ud1,ud2) for ud2 in unionDoms2]
        for ud1 in unionDoms1]
    or/[canCoerce(t1,ud) for ud in unionDoms2]
  -- next, a little lie
  t1 is ['Union,d1, ='"failed"] and t2 = d1 => true
  isUnion1 =>
    and/[canCoerce(ud,t2) for ud in unionDoms1]
  keyedSystemError("S2GE0016",['"canCoerceUnion",
     '"called with 2 non-Unions"])

canCoerceByMap(t1,t2) ==
  -- idea is this: if t1 is D U1 and t2 is D U2, then look for
  -- map: (U1 -> U2, D U1) -> D U2.  If it exists, then answer true
  -- if canCoerceFrom(t1,t2).
  u2 := deconstructT t2
  1 = #u2 => NIL
  u1 := deconstructT t1
  1 = #u1 => NIL                             -- no under domain
  first(u1) ~= first(u2) => NIL
  top := CAAR u1
  u1 := underDomainOf t1
  u2 := underDomainOf t2

  absolutelyCannotCoerce(u1,u2) => NIL

  -- save some time for those we know about
  know := '(List Vector Segment Stream UniversalSegment Array
    Polynomial UnivariatePolynomial SquareMatrix Matrix)
  top in know => canCoerce(u1,u2)

  null selectMms1('map,t2,[['Mapping,u2,u1],t1],
    [['Mapping,u2,u1],u1],NIL) => NIL
  -- don't bother checking for Undef, so avoid instantiation
  canCoerce(u1,u2)

canCoerceTower(t1,t2) ==
-- tries to find a coercion between top level t2 and somewhere inside t1
-- builds new bubbled type, for which coercion is called recursively
  canCoerceByMap(t1,t2) or newCanCoerceCommute(t1,t2) or
   canCoerceLocal(t1,t2) or canCoercePermute(t1,t2) or
    [c1,:arg1]:= deconstructT t1
    arg1 and
      TL:= NIL
      arg:= arg1
      until x or not arg repeat x:=
        t:= last arg
        [c,:arg]:= deconstructT t
        TL:= [c,arg,:TL]
        arg and coerceIntTest(t,t2) and
          CDDR TL =>
            s:= constructT(c1,replaceLast(arg1,bubbleConstructor TL))
            canCoerceLocal(t1,s) and
              [c2,:arg2]:= deconstructT last s
              s1:= bubbleConstructor [c2,arg2,c1,arg1]
              canCoerceCommute(s,s1) and canCoerceLocal(s1,t2)
          s:= bubbleConstructor [c,arg,c1,arg1]
          newCanCoerceCommute(t1,s) and canCoerceLocal(s,t2)
      x

canCoerceLocal(t1,t2) ==
  -- test for coercion on top level
  p:= ASSQ(first t1,$CoerceTable)
  p and ASSQ(first t2,rest p) is [.,:[tag,fun]] =>
    tag='partial => NIL
    tag='total   => true
    (functionp(fun) and
       (v:=CATCH('coerceFailure,FUNCALL(fun,'_$fromCoerceable_$,t1,t2)))
         and v ~= $coerceFailure)  or  canCoerceByFunction(t1,t2)
  canCoerceByFunction(t1,t2)

canCoerceCommute(t1,t2) ==
-- THIS IS OUT-MODED AND WILL GO AWAY SOON  RSS 2-87
-- t1 is t2 with the two top level constructors commuted
-- looks for the existence of a commuting function
  first(t1) in (l := [$QuotientField, 'Gaussian]) and
    first(t2) in l => true
  p:= ASSQ(first t1,$CommuteTable)
  p and ASSQ(first t2,rest p) is [.,:['commute,.]]

newCanCoerceCommute(t1,t2) ==
  coerceIntCommute(objNewWrap("$fromCoerceable$",t1),t2)

canCoercePermute(t1,t2) ==
  -- try to generate a sequence of transpositions that will convert
  -- t1 into t2
  member(t2,'((Integer) (OutputForm))) => NIL
  towers := computeTTTranspositions(t1,t2)
  -- at this point, first towers = t1 and last towers should be similar
  -- to t2 in the sense that the components of t1 are in the same order
  -- as in t2. If length towers = 2 and t2 = last towers, we quit to
  -- avoid an infinte loop.
  null towers or null rest towers => NIL
  null CDDR towers and t2 = second towers => NIL
  -- do the coercions successively, quitting if any fail
  ok := true
  for t in rest towers while ok repeat
    ok := canCoerce(t1,t)
    if ok then t1 := t
  ok

canConvertByFunction(m1,m2) ==
  null $useConvertForCoercions => NIL
  canCoerceByFunction1(m1,m2,'convert)

canCoerceByFunction(m1,m2) == canCoerceByFunction1(m1,m2,'coerce)

canCoerceByFunction1(m1,m2,fun) ==
  -- calls selectMms with $Coerce=NIL and tests for required target=m2
  $declaredMode:local:= NIL
  $reportBottomUpFlag:local:= NIL
  -- have to handle cases where we might have changed from RN to QF I
  -- make 2 lists of expanded and unexpanded types
  l1 := removeDuplicates [m1,eqType m1]
  l2 := removeDuplicates [m2,eqType m2]
  ans  := NIL
  for t1 in l1 while not ans repeat
    for t2 in l2 while not ans repeat
      l := selectMms1(fun,t2,[t1],[t1],NIL)
      ans := [x for x in l | x is [sig,:.] and second sig=t2 and
       third sig=t1 and
        first(sig) isnt ['TypeEquivalence,:.]] and true
  ans

absolutelyCanCoerceByCheating(t1,t2) ==
  -- this typically involves subdomains and towers where the only
  -- difference is a subdomain
  isEqualOrSubDomain(t1,t2) => true
  typeIsASmallInteger(t1) and t2 = $Integer => true
  atom(t1) or atom(t2) => false
  [tl1,:u1] := deconstructT t1
  [tl2,:u2] := deconstructT t2
  tl1 = '(Stream) and tl2 = '(InfiniteTuple) =>
    #u1 ~= #u2 => false
    "and"/[absolutelyCanCoerceByCheating(x1,x2) for x1 in u1 for x2 in u2]
  tl1 ~= tl2 => false
  #u1 ~= #u2 => false
  "and"/[absolutelyCanCoerceByCheating(x1,x2) for x1 in u1 for x2 in u2]

absolutelyCannotCoerce(t1,t2) ==
  -- response of true means "definitely cannot coerce"
  -- this is largely an efficiency hack
  atom(t1) or atom(t2) => NIL
  t2 = $None => true
  n1   := first t1
  n2   := first t2
  QFI  := [$QuotientField, $Integer]
  int2 := isEqualOrSubDomain(t2,$Integer)
  scalars := '(BigFloat NewFloat Float DoubleFloat RationalNumber)

  MEMQ(n1,scalars) and int2 => true
  (t1 = QFI) and int2       => true

  num2 := int2 or MEMQ(n2,scalars) or (t2 = QFI)
  isVar1 := n1 in '(Variable Symbol)

  num2 and isVar1 => true
  num2 and MEMQ(n1,$univariateDomains) => true
  num2 and MEMQ(n1,$multivariateDomains) => true
  miscpols :=  '(Polynomial ElementaryFunction SimpleAlgebraicExtension)
  num2 and MEMQ(n1,miscpols) => true

  aggs :=  '(
    Matrix List Vector Stream Array RectangularMatrix FiniteSet
       )
  u1 := underDomainOf t1
  u2 := underDomainOf t2
  MEMQ(n1,aggs) and (u1 = t2) => true
  MEMQ(n2,aggs) and (u2 = t1) => true

  algs :=  '(
    SquareMatrix Gaussian RectangularMatrix Quaternion
       )
  nonpols := append(aggs,algs)
  num2 and MEMQ(n1,nonpols) => true
  isVar1 and MEMQ(n2,nonpols) and
    absolutelyCannotCoerce(t1,u2) => true

  (MEMQ(n1,scalars) or (t1 = QFI)) and (t2 = '(Polynomial (Integer))) =>
    true

  v2 := deconstructT t2
  1 = #v2 => NIL
  v1 := deconstructT t1
  1 = #v1 => NIL
  first(v1) ~= first(v2) => NIL
  absolutelyCannotCoerce(u1,u2)

typeIsASmallInteger x == (x = $SingleInteger)


--% Interpreter Coercion Functions

coerceInteractive(triple,t2) ==
  -- bind flag for recording/reporting instantiations
  -- (see recordInstantiation)
  t1 := objMode triple
  val := objVal triple
  null(t2) or t2 = $EmptyMode => NIL
  t2 = t1 => triple
  t2 = '$NoValueMode => objNew(val,t2)
  if t2 is ['SubDomain,x,.] then t2:= x
  -- JHD added category Aug 1996 for BasicMath
  member(t1,$LangSupportTypes) =>
    t2 = $OutputForm => objNew(val,t2)
    t1 = $Domain and conceptualType t2 = $Category 
      and ofCategory(val,t2)=> objNew(val,t2)
    conceptualType t1 = t2 => objNew(val,t2)
    nil
  t1 = '$NoValueMode =>
    if $compilingMap then clearDependentMaps($mapName,nil)
    throwKeyedMsg("S2IC0009",[t2,$mapName])
  $insideCoerceInteractive: local := true
  expr2 := EQUAL(t2,$OutputForm)
  if expr2 then startTimingProcess 'print
  else startTimingProcess 'coercion
  -- next 2 lines handle cases like '"failed"
  result :=
    expr2 and (t1 = val) => objNew(val,$OutputForm)
    expr2 and t1 is ['Variable,var] => objNewWrap(var,$OutputForm)
    coerceInt0(triple,t2)
  if expr2 then stopTimingProcess 'print
  else stopTimingProcess 'coercion
  result

coerceInt0(triple,t2) ==
  -- top level interactive coercion, which transfers all RN, RF and RR
  -- into equivalent types
  val := objVal triple
  t1  := objMode triple

  val='_$fromCoerceable_$ => canCoerceFrom(t1,t2)
  t1 = t2 => triple
  if t2 = $OutputForm then
    s1 := t1
    s2 := t2
  else
    s1 := equiType(t1)
    s2 := equiType(t2)
    s1 = s2 => return objNew(val,t2)
  -- t1 is ['Mapping,:.] and t2 ~= '(Any) => NIL
  -- note: may be able to coerce TO mapping
  -- treat Exit like Any
  -- handle case where we must generate code
  null(isWrapped val) and
    (t1 isnt ['FunctionCalled,:.] or not $genValue)=>
      intCodeGenCOERCE(triple,t2)
  t1 = $Any and t2 ~= $OutputForm and ([t1',:val'] := unwrap val) and
    (ans := coerceInt0(objNewWrap(val',t1'),t2)) => ans
  if not EQ(s1,t1) then triple := objNew(val,s1)
  x := coerceInt(triple,s2) =>
    EQ(s2,t2) => x
    objSetMode(x,t2)
    x
  NIL

coerceInt(triple, t2) ==
  val := coerceInt1(triple, t2) => val
  t1 := objMode triple
  t1 is ['Variable, :.] =>
    newMode := getMinimalVarMode(unwrap objVal triple, nil)
    newVal := coerceInt(triple, newMode)
    coerceInt(newVal, t2)
  nil

coerceInt1(triple,t2) ==
  -- general interactive coercion
  -- the result is a new triple with type m2 or NIL (= failed)
  $useCoerceOrCroak: local := true
  t2 = $EmptyMode => NIL
  t1 := objMode triple
  t1=t2 => triple
  val := objVal triple
  absolutelyCanCoerceByCheating(t1,t2) => objNew(val,t2)
  isSubDomain(t2, t1) => coerceSubDomain(val, t1, t2)

  if typeIsASmallInteger(t1) then
    (t2 = $Integer) or typeIsASmallInteger(t2) => return objNew(val,t2)
    sintp := SINTP val
    sintp and (t2 = $PositiveInteger) and val > 0 => return objNew(val,t2)
    sintp and (t2 = $NonNegativeInteger) and val >= 0 => return objNew(val,t2)

  typeIsASmallInteger(t2) and isEqualOrSubDomain(t1, $Integer) and INTP val =>
    SINTP val => objNew(val,t2)
    NIL

  t2 = $Void => objNew(voidValue(),$Void)
  t2 = $Any => objNewWrap([t1,:unwrap val],$Any)

  t1 = $Any and t2 ~= $OutputForm and ([t1',:val'] := unwrap val) and
    (ans := coerceInt(objNewWrap(val',t1'),t2)) => ans

  -- next is for tagged union selectors for the time being
  t1 is ['Variable,=t2] or t2 is ['Variable,=t1] => objNew(val,t2)

  string? t2 =>
    t1 is ['Variable,v] and (t2 = PNAME(v)) => objNewWrap(t2,t2)
    val' := unwrap val
    (t2 = val') and ((val' = t1) or (t1 = $String)) => objNew(val,t2)
    NIL
  t1 is ['Union,:.] => coerceIntFromUnion(triple,t2)
  t2 is ['Union,:.] => coerceInt2Union(triple,t2)
  (string? t1) and (t2 = $String) => objNew(val,$String)
  (string? t1) and (t2 is ['Variable,v]) =>
    t1 = PNAME(v) => objNewWrap(v,t2)
    NIL
  (string? t1) and (t1 = unwrap val) =>
    t2 = $OutputForm => objNew(t1,$OutputForm)
    NIL
  atom t1 => NIL

  if t1 = $AnonymousFunction and (t2 is ['Mapping,target,:margl]) then
    $useCoerceOrCroak := nil
    [.,vars,:body] := unwrap val
    vars :=
      atom vars => [vars]
      vars is ["tuple",:.] => rest vars
      vars
    #margl ~= #vars => 'continue
    tree := mkAtree ['ADEF,vars,[target,:margl],[NIL for x in rest t2],:body]
    CATCH('coerceOrCroaker, bottomUp tree) = 'croaked => nil
    return getValue tree

  (t1 = $Symbol) and (t2 is ['Mapping,target,:margl]) =>
    null (mms := selectMms1(unwrap val,nil,margl,margl,target)) => NIL
    [dc,targ,:argl] := CAAR mms
    targ ~= target => NIL
    $genValue =>
      fun := getFunctionFromDomain(unwrap val,dc,argl)
      objNewWrap(fun,t2)
    val := NRTcompileEvalForm(unwrap val, rest CAAR mms, evalDomain dc)
    objNew(val, t2)
  (t1 is ['Variable,sym]) and (t2 is ['Mapping,target,:margl]) =>
    null (mms := selectMms1(sym,target,margl,margl,NIL)) => 
       null (mms := selectMms1(sym,target,margl,margl,true)) => NIL
    [dc,targ,:argl] := CAAR mms
    targ ~= target => NIL
    dc is ["__FreeFunction__",:freeFun] => objNew( freeFun, t2 )
    $genValue => objNewWrap( getFunctionFromDomain(sym,dc,argl), t2 )
    val := NRTcompileEvalForm(sym, rest CAAR mms, evalDomain dc)
    objNew(val, t2)
  (t1 is ['FunctionCalled,sym]) and (t2 is ['Mapping,target,:margl]) =>
    symNode := mkAtreeNode sym
    transferPropsToNode(sym,symNode)
    null (mms := selectLocalMms(symNode,sym,margl,target)) => NIL
    [dc,targ,:argl] := CAAR mms
    targ ~= target => NIL
    ml := [target,:margl]
    intName :=
      or/[mm for mm in mms | (mm is [[., :ml1],oldName,:.]
        and compareTypeLists(ml1,ml))] => [oldName]
      NIL
    null intName => NIL
    objNewWrap(intName,t2)
  (t1 is ['FunctionCalled,sym]) =>
    (t3 := get(sym,'mode,$e)) and t3 is ['Mapping,:.] =>
      (triple' := coerceInt(triple,t3)) => coerceInt(triple',t2)
      NIL
    NIL

  EQ(first(t1),'Variable) and cons?(t2) and
    (isEqualOrSubDomain(t2,$Integer) or
      (t2 = [$QuotientField, $Integer]) or MEMQ(first(t2),
        '(RationalNumber BigFloat NewFloat Float DoubleFloat))) => NIL

  ans := coerceRetract(triple,t2) or coerceIntTower(triple,t2) or
    [.,:arg]:= deconstructT t2
    arg and
      t:= coerceInt(triple,last arg)
      t and coerceByFunction(t,t2)
  ans or (isSubDomain(t1,$Integer) and
    coerceInt(objNew(val,$Integer),t2)) or
      coerceIntAlgebraicConstant(triple,t2) or
        coerceIntX(val,t1,t2)

coerceSubDomain(val, tSuper, tSub) ==
  -- Try to coerce from a sub domain to a super domain
  val = '_$fromCoerceable_$ => nil
  pred := isSubDomain(tSub,tSuper) =>
    predFun := getSubDomainPredicate(tSuper,tSub,pred)
    FUNCALL(predFun,val) => objNew(val,tSub)
  nil

getSubDomainPredicate(tSuper, tSub, pred) ==
  predfn := HGET($superHash, [tSuper,:tSub]) => predfn
  arg := gensym()
  [predfn] := compileInteractive
                [gensym(),['LAM,[arg],substitute(arg,"#1", pred)]]
  HPUT($superHash, [tSuper,:tSub], predfn)
  predfn

coerceIntX(val,t1, t2) ==
  -- some experimental things
  t1 = '(List (None)) =>
    -- this will almost always be an empty list
    null unwrap val =>
      -- try getting a better flavor of List
      null (t0 := underDomainOf(t2)) => NIL
      coerceInt(objNewWrap(val,['List,t0]),t2)
    NIL
  NIL

compareTypeLists(tl1,tl2) ==
  -- returns true if every type in tl1 is = or is a subdomain of
  -- the corresponding type in tl2
  for t1 in tl1 for t2 in tl2 repeat
    null isEqualOrSubDomain(t1,t2) => return NIL
  true

coerceIntAlgebraicConstant(object,t2) ==
  -- should use = from domain, but have to check on defaults code
  t1 := objMode object
  val := objValUnwrap object
  ofCategory(t1,'(Monoid)) and ofCategory(t2,'(Monoid)) and
    val = getConstantFromDomain('(One),t1) =>
      objNewWrap(getConstantFromDomain('(One),t2),t2)
  ofCategory(t1,'(AbelianMonoid)) and ofCategory(t2,'(AbelianMonoid)) and
    val = getConstantFromDomain('(Zero),t1) =>
      objNewWrap(getConstantFromDomain('(Zero),t2),t2)
  NIL

++ returns true if `val' belongs to the Union branch guarded by `pred'.
thisUnionBranch?: (%Code,%Thing) -> %Boolean
thisUnionBranch?(pred,val) ==
  eval ["LET",[["#1",MKQ val]],pred]

coerceUnion2Branch(object) ==
  [.,:doms] := objMode object
  predList:= mkPredList doms
  doms := stripUnionTags doms
  val' := objValUnwrap object
  predicate := NIL
  targetType:= NIL
  for typ in doms for pred in predList while null targetType repeat
    thisUnionBranch?(pred,val') =>
      predicate := pred
      targetType := typ
  null targetType => keyedSystemError("S2IC0013",NIL)
  predicate is ['%ieq,['%head,.],p] => objNewWrap(rest val',targetType)
  objNew(objVal object,targetType)

coerceBranch2Union(object,union) ==
  -- assumes type is a member of doms
  doms := rest union
  predList:= mkPredList doms
  doms := stripUnionTags doms
  p := position(objMode object,doms)
  p = -1 => keyedSystemError("S2IC0014",[objMode object,union])
  val := objVal object
  predList.p is ['%ieq,['%head,.],tag] =>
    objNewWrap([removeQuote tag,:unwrap val],union)
  objNew(val,union)

coerceInt2Union(object,union) ==
  -- coerces to a Union type, adding numeric tags
  -- first cut
  unionDoms := stripUnionTags rest union
  t1 := objMode object
  member(t1,unionDoms) => coerceBranch2Union(object,union)
  val := objVal object
  val' := unwrap val
  (t1 = $String) and member(val',unionDoms) =>
    coerceBranch2Union(objNew(val,val'),union)
  noCoerce := true
  val' := nil
  for d in unionDoms while noCoerce repeat
    (val' := coerceInt(object,d)) => noCoerce := nil
  val' => coerceBranch2Union(val',union)
  NIL

coerceIntFromUnion(object,t2) ==
  -- coerces from a Union type to something else
  coerceInt(coerceUnion2Branch object,t2)

coerceIntByMap(triple,t2) ==
  -- idea is this: if t1 is D U1 and t2 is D U2, then look for
  -- map: (U1 -> U2, D U1) -> D U2.  If it exists, then create a
  -- function to do the coercion on the element level and call the
  -- map function.
  t1 := objMode triple
  t2 = t1 => triple
  u2 := deconstructT t2    -- compute t2 first because of Expression
  1 = #u2 => NIL           -- no under domain
  u1 := deconstructT t1
  1 = #u1 => NIL
  CAAR u1 ~= CAAR u2 => nil  -- constructors not equal
  not valueArgsEqual?(t1, t2) => NIL
--  first u1 ~= first u2 => NIL
  top := CAAR u1
  u1 := underDomainOf t1
  u2 := underDomainOf t2

  -- handle a couple of special cases for subdomains of Integer
  top in '(List Vector Segment Stream UniversalSegment Array)
    and isSubDomain(u1,u2) => objNew(objVal triple, t2)

  args := [['Mapping,u2,u1],t1]
  if $reportBottomUpFlag then
    sayFunctionSelection('map,args,t2,NIL,
      '"coercion facility (map)")
  mms := selectMms1('map,t2,args,args,NIL)
  if $reportBottomUpFlag then
    sayFunctionSelectionResult('map,args,mms)
  null mms => NIL

  [[dc,:sig],slot,.]:= first mms
  fun := compiledLookup('map,sig,evalDomain(dc))
  null fun => NIL
  [fn,:d]:= fun
  fn = function Undef => NIL
  -- now compile a function to do the coercion
  code := ['SPADCALL,['CONS,["function","coerceIntByMapInner"],MKQ [u1,:u2]],
    getValueNormalForm triple,MKQ fun]
  -- and apply the function
  val := CATCH('coerceFailure,timedEvaluate code)
  (val = $coerceFailure) => NIL
  objNewWrap(val,t2)

coerceIntByMapInner(arg,[u1,:u2]) == coerceOrThrowFailure(arg,u1,u2)
-- [u1,:u2] gets passed as the "environment", which is why we have this
-- slightly clumsy locution  JHD 31.July,1990

valueArgsEqual?(t1, t2) ==
  -- returns true if the object-valued arguments to t1 and t2 are the same
  -- under coercion
  coSig := rest getDualSignatureFromDB first t1
  constrSig := rest getConstructorSignature first t1
  tl1 := replaceSharps(constrSig, t1)
  tl2 := replaceSharps(constrSig, t2)
  not MEMQ(NIL, coSig) => true
  done := false
  value := true
  for a1 in rest t1 for a2 in rest t2 for cs in coSig
    for m1 in tl1 for m2 in tl2 while not done repeat
          not cs =>
            trip := objNewWrap(a1, m1)
            newVal := coerceInt(trip, m2)
            null newVal => (done := true; value := false)
            not algEqual(a2, objValUnwrap newVal, m2) =>
              (done := true; value := false)
  value

coerceIntTower(triple,t2) ==
  -- tries to find a coercion from top level t2 to somewhere inside t1
  -- builds new argument type, for which coercion is called recursively
  x := coerceIntByMap(triple,t2) => x
  x := coerceIntCommute(triple,t2) => x
  x := coerceIntPermute(triple,t2) => x
  x := coerceIntSpecial(triple,t2) => x
  x := coerceIntTableOrFunction(triple,t2) => x
  t1 := objMode triple
  [c1,:arg1]:= deconstructT t1
  arg1 and
    TL:= NIL
    arg:= arg1
    until x or not arg repeat
      t:= last arg
      [c,:arg]:= deconstructT t
      TL:= [c,arg,:TL]
      x := arg and coerceIntTest(t,t2) =>
        CDDR TL =>
          s := constructT(c1,replaceLast(arg1,bubbleConstructor TL))
          (null isValidType(s)) => (x := NIL)
          x := (coerceIntByMap(triple,s) or
            coerceIntTableOrFunction(triple,s)) =>
              [c2,:arg2]:= deconstructT last s
              s:= bubbleConstructor [c2,arg2,c1,arg1]
              (null isValidType(s)) => (x := NIL)
              x:= coerceIntCommute(x,s) =>
                x := (coerceIntByMap(x,t2) or
                  coerceIntTableOrFunction(x,t2))
        s:= bubbleConstructor [c,arg,c1,arg1]
        (null isValidType(s)) => (x := NIL)
        x:= coerceIntCommute(triple,s) =>
          x:= (coerceIntByMap(x,t2) or
            coerceIntTableOrFunction(x,t2))
    x

coerceIntSpecial(triple,t2) ==
  t1 := objMode triple
  t2 is ['SimpleAlgebraicExtension,R,U,.] and t1 = R =>
    null (x := coerceInt(triple,U)) => NIL
    coerceInt(x,t2)
  NIL

coerceIntTableOrFunction(triple,t2) ==
  -- this function does the actual coercion to t2, but not to an
  -- argument type of t2
  null isValidType t2 => NIL  -- added 9-18-85 by RSS
  null isLegitimateMode(t2,NIL,NIL) => NIL  -- added 6-28-87 by RSS
  t1 := objMode triple
  p:= ASSQ(first t1,$CoerceTable)
  p and ASSQ(first t2,rest p) is [.,:[tag,fun]] =>
    val := objVal triple
    fun='Identity => objNew(val,t2)
    tag='total =>
      coerceByTable(fun,val,t1,t2,'T) or coerceByFunction(triple,t2)
    coerceByTable(fun,val,t1,t2,NIL) or coerceByFunction(triple,t2)
  coerceByFunction(triple,t2)

coerceCommuteTest(t1,t2) ==
  null isLegitimateMode(t2,NIL,NIL) => NIL

  -- sees whether t1 = D1 D2 R and t2 = D2 D1 S
  null (u1 := underDomainOf t1) => NIL
  null (u2 := underDomainOf t2) => NIL

  -- must have underdomains (ie, R and S must be there)

  null (v1 := underDomainOf u1) => NIL
  null (v2 := underDomainOf u2) => NIL

  -- now check that cross of constructors is correct
  (first(deconstructT t1) = first(deconstructT u2)) and
    (first(deconstructT t2) = first(deconstructT u1))

coerceIntCommute(obj,target) ==
  -- note that the value in obj may be $fromCoerceable$, for canCoerce
  source := objMode obj
  null coerceCommuteTest(source,target) => NIL
  S := underDomainOf source
  T := underDomainOf target
  source = T => NIL      -- handle in other ways

  source is [D,:.] =>
    fun := GETL(D,'coerceCommute) or
           INTERN strconc('"commute",STRINGIMAGE D)
    functionp fun =>
      PUT(D,'coerceCommute,fun)
      u := objValUnwrap obj
      c := CATCH('coerceFailure,FUNCALL(fun,u,source,S,target,T))
      (c = $coerceFailure) => NIL
      u = "$fromCoerceable$" => c
      objNewWrap(c,target)
    NIL
  NIL

coerceIntPermute(object,t2) ==
  member(t2,'((Integer) (OutputForm))) => NIL
  t1 := objMode object
  towers := computeTTTranspositions(t1,t2)
  -- at this point, first towers = t1 and last towers should be similar
  -- to t2 in the sense that the components of t1 are in the same order
  -- as in t2. If length towers = 2 and t2 = last towers, we quit to
  -- avoid an infinte loop.
  null towers or null rest towers => NIL
  null CDDR towers and t2 = second towers => NIL
  -- do the coercions successively, quitting if any fail
  ok := true
  for t in rest towers while ok repeat
    null (object := coerceInt(object,t)) => ok := NIL
  ok => object
  NIL

computeTTTranspositions(t1,t2) ==
  -- decompose t1 into its tower parts
  tl1 := decomposeTypeIntoTower t1
  tl2 := decomposeTypeIntoTower t2
  -- if not at least 2 parts, don't bother working here
  null (rest tl1 and rest tl2) => NIL
  -- determine the relative order of the parts of t1 in t2
  p2 := [position(d1,tl2) for d1 in tl1]
  member(-1,p2) => NIL            -- something not present
  -- if they are all ascending, this function will do nothing
  p2' := MSORT p2
  p2 = p2' => NIL
  -- if anything is repeated twice, leave
  p2' ~= MSORT removeDuplicates p2' => NIL
  -- create a list of permutations that transform the tower parts
  -- of t1 into the order they are in in t2
  n1 := #tl1
  p2 := LIST2VEC compress(p2,0,# removeDuplicates tl1) where
    compress(l,start,len) ==
      start >= len => l
      member(start,l) => compress(l,start+1,len)
      compress([(i < start => i; i - 1) for i in l],start,len)
  -- p2 now has the same position numbers as p1, we need to determine
  -- a list of permutations that takes p1 into p2.
  -- them
  perms := permuteToOrder(p2,n1-1,0)
  towers := [tl1]
  tower := LIST2VEC tl1
  for perm in perms repeat
    t := tower.(first perm)
    tower.(first perm) := tower.(rest perm)
    tower.(rest perm) := t
    towers := [VEC2LIST tower,:towers]
  towers := [reassembleTowerIntoType tower for tower in towers]
  if first(towers) ~= t2 then towers := [t2,:towers]
  nreverse towers

decomposeTypeIntoTower t ==
  atom t => [t]
  d := deconstructT t
  null rest d => [t]
  rd := reverse t
  [reverse rest rd,:decomposeTypeIntoTower first rd]

reassembleTowerIntoType tower ==
  atom tower => tower
  null rest tower => first tower
  [:top,t,s] := tower
  reassembleTowerIntoType [:top,[:t,s]]

permuteToOrder(p,n,start) ==
  -- p is a vector of the numbers 0..n. This function returns a list
  -- of swaps of adjacent elements so that p will be in order. We only
  -- begin looking at index start
  r := n - start
  r <= 0 => NIL
  r = 1 =>
    p.r < p.(r+1) => NIL
    [[r,:(r+1)]]
  p.start = start => permuteToOrder(p,n,start+1)
  -- bubble up element start to the top. Find out where it is
  stpos := NIL
  for i in start+1..n while not stpos repeat
    if p.i = start then stpos := i
  perms := NIL
  while stpos ~= start repeat
    x := stpos - 1
    perms := [[x,:stpos],:perms]
    t := p.stpos
    p.stpos := p.x
    p.x := t
    stpos := x
  append(nreverse perms,permuteToOrder(p,n,start+1))

coerceIntTest(t1,t2) ==
  -- looks whether there exists a table entry or a coercion function
  -- thus the type can be bubbled before coerceIntTableOrFunction is called
  t1=t2 or
    b:=
      p:= ASSQ(first t1,$CoerceTable)
      p and ASSQ(first t2,rest p)
    b or coerceConvertMmSelection('coerce,t1,t2) or
      ($useConvertForCoercions and
        coerceConvertMmSelection('convert,t1,t2))

coerceByTable(fn,x,t1,t2,isTotalCoerce) ==
  -- catch point for 'failure in boot coercions
  t2 = $OutputForm and not (newType? t1) => NIL
  isWrapped x =>
    x:= unwrap x
    c:= CATCH('coerceFailure,FUNCALL(fn,x,t1,t2))
    c=$coerceFailure => NIL
    objNewWrap(c,t2)
  isTotalCoerce => objNew([fn,x,MKQ t1,MKQ t2],t2)
  objNew(['catchCoerceFailure,MKQ fn,x,MKQ t1,MKQ t2],t2)

catchCoerceFailure(fn,x,t1,t2) ==
  -- compiles a catchpoint for compiling boot coercions
  c:= CATCH('coerceFailure,FUNCALL(fn,x,t1,t2))
  c = $coerceFailure =>
    throwKeyedMsgCannotCoerceWithValue(wrap unwrap x,t1,t2)
  c

coercionFailure() ==
  -- does the throw on coercion failure
  THROW('coerceFailure,$coerceFailure)

coerceByFunction(T,m2) ==
  -- using the new modemap selection without coercions
  -- should not be called by canCoerceFrom
  x := objVal T
  x = '_$fromCoerceable_$ => NIL
  m2 is ['Union,:.] => NIL
  m1 := objMode T
  m2 is ['Boolean,:.] and m1 is ['Equation,ud] =>
    dcVector := evalDomain ud
    fun :=
      isWrapped x =>
        NRTcompiledLookup("=", [$Boolean, '$, '$], dcVector)
      NRTcompileEvalForm("=", [$Boolean, '$, '$], dcVector)
    [fn,:d]:= fun
    isWrapped x =>
      x:= unwrap x
      objNewWrap(SPADCALL(first x,rest x,fun),m2)
    x isnt ['SPADCALL,a,b,:.] => keyedSystemError("S2IC0015",NIL)
    code := ['SPADCALL, a, b, fun]
    objNew(code,$Boolean)
  -- If more than one function is found, any should suffice, I think -scm
  if not (mm := coerceConvertMmSelection(funName := 'coerce,m1,m2)) then
    mm := coerceConvertMmSelection(funName := 'convert,m1,m2)
  mm =>
    [[dc,tar,:args],slot,.]:= mm
    dcVector := evalDomain(dc)
    fun:=
--+
      isWrapped x =>
        NRTcompiledLookup(funName,slot,dcVector)
      NRTcompileEvalForm(funName,slot,dcVector)
    [fn,:d]:= fun
    fn = function Undef => NIL
    isWrapped x =>
--+
      $: fluid := dcVector
      val := CATCH('coerceFailure, SPADCALL(unwrap x,fun))
      (val = $coerceFailure) => NIL
      objNewWrap(val,m2)
    env := fun
    code := ['failCheck, ['SPADCALL, x, env]]
--  tar is ['Union,:.] => objNew(['failCheck,code],m2)
    objNew(code,m2)
  -- try going back to types like RN instead of QF I
  m1' := eqType m1
  m2' := eqType m2
  (m1 ~= m1') or (m2 ~= m2') => coerceByFunction(objNew(x,m1'),m2')
  NIL

hasCorrectTarget(m,sig is [dc,tar,:.]) ==
  -- tests whether the target of signature sig is either m or a union
  -- containing m. It also discards TEQ as it is not meant to be
  -- used at top-level
  dc is ['TypeEquivalence,:.] => NIL
  m=tar => 'T
  tar is ['Union,t,'failed] => t=m
  tar is ['Union,'failed,t] and t=m

