import { NextResponse } from "next/server";
import { docsCatalogJson } from "@/lib/documentation/catalog";

export async function GET() {
  return NextResponse.json(docsCatalogJson());
}
